import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "GET") return Response.json({ message: "Method not allowed." }, { status: 405, headers: cors });
  const url = new URL(req.url);
  const id = (url.searchParams.get("id") ?? "").trim();
  if (!id || id.length > 100 || !/^[A-Za-z0-9_-]+$/.test(id)) {
    return Response.json({ message: "Enter a valid report ID." }, { status: 400, headers: cors });
  }
  const endpoint = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!endpoint || !serviceKey) {
    return Response.json({ message: "Verification service is not configured." }, { status: 503, headers: cors });
  }
  const supabase = createClient(endpoint, serviceKey, { auth: { persistSession: false } });
  const { data, error } = await supabase.rpc("verify_current_report", { p_public_id: id });
  if (error) {
    console.error("verify_current_report failed", error.message);
    return Response.json({ message: "Verification is temporarily unavailable." }, { status: 503, headers: cors });
  }
  if (!data) return Response.json({ message: "No current valid report was found for this ID. It may be incorrect, replaced, or revoked.", valid: false }, { status: 404, headers: cors });
  return Response.json({ message: "This report is currently valid.", valid: true, report: data }, { headers: cors });
});
