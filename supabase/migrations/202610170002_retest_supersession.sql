-- Retest policy: the previous report and detailed test evidence become unavailable
-- as soon as a new test for the same verified identifier starts. Keep only a minimal
-- non-sensitive audit tombstone; never expose old reports as current.

CREATE TABLE IF NOT EXISTS private_object_cleanup_queue (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  bucket_id TEXT NOT NULL,
  object_key TEXT NOT NULL,
  queued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);

CREATE TABLE IF NOT EXISTS device_current_report (
  device_id UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
  certificate_id UUID UNIQUE REFERENCES certificates(id) ON DELETE CASCADE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Public verification must query device_current_report -> certificates and return
-- only the mapped, valid certificate. Do not query old report IDs from object storage.

CREATE OR REPLACE FUNCTION begin_device_retest(p_device_id UUID, p_new_session_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_old_cert_id UUID;
  v_old_session_id UUID;
  v_old_public_id TEXT;
  v_old_pdf_key TEXT;
BEGIN
  -- The API must authenticate the operator/session and validate the serial HMAC before calling.
  IF NOT EXISTS (
    SELECT 1 FROM test_sessions s
    WHERE s.id = p_new_session_id AND s.device_id = p_device_id
      AND s.session_status IN ('created','running')
  ) THEN
    RAISE EXCEPTION 'New test session does not belong to this device or is not active';
  END IF;

  SELECT c.id, c.session_id, c.public_id, c.pdf_object_key
    INTO v_old_cert_id, v_old_session_id, v_old_public_id, v_old_pdf_key
  FROM device_current_report cur
  JOIN certificates c ON c.id = cur.certificate_id
  WHERE cur.device_id = p_device_id
  FOR UPDATE OF cur;

  IF v_old_cert_id IS NOT NULL THEN
    -- Queue private PDF deletion for a trusted worker; never expose the object key publicly.
    INSERT INTO private_object_cleanup_queue(bucket_id, object_key)
    VALUES ('verity-reports', v_old_pdf_key);

    -- Keep a minimal audit marker, not old test values, serial, PDF, or public report ID.
    INSERT INTO audit_events(session_id, certificate_id, event_type, event_data)
    VALUES (
      p_new_session_id, NULL, 'previous_report_data_removed_for_retest',
      jsonb_build_object(
        'previous_report_id_hash', encode(digest(v_old_public_id, 'sha256'), 'hex'),
        'previous_report_removed', true,
        'reason', 'new_test_for_same_device'
      )
    );

    DELETE FROM delivery_jobs WHERE certificate_id = v_old_cert_id;
    DELETE FROM audit_events WHERE certificate_id = v_old_cert_id OR session_id = v_old_session_id;
    DELETE FROM test_results WHERE session_id = v_old_session_id;
    DELETE FROM certificates WHERE id = v_old_cert_id;
    DELETE FROM test_sessions WHERE id = v_old_session_id;

    -- Database deletion immediately makes the old report ID fail public verification.
    -- A trusted worker must drain private_object_cleanup_queue through the Storage API and retry failures.
  END IF;

  DELETE FROM device_current_report WHERE device_id = p_device_id;
  UPDATE test_sessions SET session_status = 'running', started_at = COALESCE(started_at, now())
    WHERE id = p_new_session_id;
END;
$$;

REVOKE ALL ON FUNCTION begin_device_retest(UUID, UUID) FROM PUBLIC, anon, authenticated;
-- Grant EXECUTE only to the trusted server/service role after deployment review.

-- Certificate issuance must update device_current_report in the same server-side transaction.
-- Example (trusted backend only):
-- INSERT INTO device_current_report(device_id, certificate_id)
-- VALUES (:device_id, :certificate_id)
-- ON CONFLICT (device_id) DO UPDATE SET certificate_id = EXCLUDED.certificate_id, updated_at = now();

-- Keep browser clients away from raw tables. Trusted server functions use service_role.
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE test_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE certificates ENABLE ROW LEVEL SECURITY;
ALTER TABLE test_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE delivery_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE seller_leads ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_current_report ENABLE ROW LEVEL SECURITY;
ALTER TABLE private_object_cleanup_queue ENABLE ROW LEVEL SECURITY;
GRANT EXECUTE ON FUNCTION begin_device_retest(UUID, UUID) TO service_role;

ALTER TABLE certificates ADD COLUMN IF NOT EXISTS public_summary JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE OR REPLACE FUNCTION verify_current_report(p_public_id TEXT)
RETURNS JSONB
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
  SELECT jsonb_build_object(
    'status', 'valid',
    'report_id', c.public_id,
    'issued_at', c.issued_at,
    'coverage_profile', c.certification_level,
    'phone_name', d.mobile_name,
    'model_name', d.model_name,
    'serial_last4', d.identifier_last4,
    'summary', c.public_summary
  )
  FROM device_current_report cur
  JOIN certificates c ON c.id = cur.certificate_id
  JOIN test_sessions s ON s.id = c.session_id
  JOIN devices d ON d.id = cur.device_id AND d.id = s.device_id
  WHERE c.public_id = p_public_id AND c.certificate_status = 'valid'
  LIMIT 1;
$$;
REVOKE ALL ON FUNCTION verify_current_report(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION verify_current_report(TEXT) TO anon, authenticated, service_role;
