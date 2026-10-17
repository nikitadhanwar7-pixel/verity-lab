-- VERITY Lab backend starter schema. PostgreSQL + pgcrypto.
-- Do not expose database credentials or signing keys to browser code.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  identifier_type TEXT NOT NULL CHECK (identifier_type IN ('serial','imei','unknown')),
  identifier_lookup_hmac BYTEA UNIQUE,
  identifier_ciphertext BYTEA,
  identifier_last4 TEXT,
  mobile_name TEXT NOT NULL,
  model_name TEXT,
  model_number TEXT,
  identity_source TEXT NOT NULL DEFAULT 'user_declared',
  seller_contact_ciphertext BYTEA,
  seller_contact_consent BOOLEAN NOT NULL DEFAULT FALSE,
  public_listing_consent BOOLEAN NOT NULL DEFAULT FALSE,
  state_name TEXT,
  city_name TEXT,
  postal_code TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE test_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id UUID REFERENCES devices(id),
  resume_token_hash BYTEA NOT NULL,
  test_suite_version TEXT NOT NULL,
  platform TEXT NOT NULL CHECK (platform IN ('ios','android','other')),
  payment_provider TEXT,
  payment_reference TEXT UNIQUE,
  payment_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (payment_status IN ('pending','paid','failed','refunded')),
  session_status TEXT NOT NULL DEFAULT 'created'
    CHECK (session_status IN ('created','running','paused','completed','expired','aborted')),
  last_checkpoint_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE certificates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  public_id TEXT NOT NULL UNIQUE,
  session_id UUID NOT NULL UNIQUE REFERENCES test_sessions(id),
  certificate_status TEXT NOT NULL DEFAULT 'valid'
    CHECK (certificate_status IN ('valid','revoked','superseded')),
  certification_level TEXT NOT NULL,
  passed_count INTEGER NOT NULL DEFAULT 0,
  attention_count INTEGER NOT NULL DEFAULT 0,
  failed_count INTEGER NOT NULL DEFAULT 0,
  not_tested_count INTEGER NOT NULL DEFAULT 0,
  pdf_object_key TEXT NOT NULL,
  pdf_sha256 BYTEA NOT NULL,
  manifest_sha256 BYTEA NOT NULL,
  signature BYTEA NOT NULL,
  signing_key_id TEXT NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE test_results (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id UUID NOT NULL REFERENCES test_sessions(id),
  test_code TEXT NOT NULL,
  category TEXT NOT NULL,
  outcome TEXT NOT NULL CHECK (outcome IN ('PASS','ATTENTION','FAIL','NOT_TESTED')),
  measured_value JSONB,
  evidence_hash BYTEA,
  limitation TEXT NOT NULL,
  duration_ms INTEGER,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, test_code)
);

CREATE TABLE delivery_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  certificate_id UUID NOT NULL REFERENCES certificates(id),
  channel TEXT NOT NULL CHECK (channel IN ('download','whatsapp')),
  destination_ciphertext BYTEA,
  status TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','sent','failed','delivered')),
  provider_message_id TEXT,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error_code TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE seller_leads (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id UUID NOT NULL REFERENCES devices(id),
  buyer_contact_ciphertext BYTEA NOT NULL,
  seller_notification_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (seller_notification_status IN ('pending','sent','failed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_id UUID REFERENCES test_sessions(id),
  certificate_id UUID REFERENCES certificates(id),
  event_type TEXT NOT NULL,
  event_data JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX certificates_issued_at_idx ON certificates (issued_at DESC);
CREATE INDEX test_results_session_idx ON test_results (session_id, category);
CREATE INDEX audit_events_session_idx ON audit_events (session_id, created_at);
CREATE INDEX devices_public_directory_idx ON devices (public_listing_consent, created_at DESC);
CREATE INDEX seller_leads_device_idx ON seller_leads (device_id, created_at DESC);

-- Security / trust rules:
-- 1. Compute identifier_lookup_hmac using a server-only secret. Encrypt raw serial/IMEI at rest.
-- 2. The requested public listing reveals the full serial only after explicit public_listing_consent.
-- 3. Seller contact details are private; reveal/use them only under explicit seller consent.
-- 4. Buyer contact numbers and WhatsApp destinations must be encrypted at rest and access-controlled.
-- 5. Payment status changes only after a verified provider webhook/server-to-server confirmation.
-- 6. Never trust client-supplied payment status, test outcomes, timestamps or report PDFs.
-- 7. Keep PDF storage private; issue short-lived access links and verify signed report hashes.
-- 8. Add least-privilege roles, rate limits, row-level security and retention/deletion workflows before deployment.
-- 9. A browser session checkpoint is not a server-side diagnostic job; tests need uploaded evidence before server finalization.
