import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });
  if (req.method !== "POST") return json({ error: "Method not allowed." }, 405);

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const authHeader = req.headers.get("Authorization") || "";
  const token = authHeader.replace(/^Bearer\s+/i, "");
  if (!url || !anonKey || !serviceKey || !token) return json({ error: "Server configuration or admin session is missing." }, 500);

  try {
    const authClient = createClient(url, anonKey, { global: { headers: { Authorization: "Bearer " + token } }, auth: { persistSession: false, autoRefreshToken: false } });
    const { data: authData, error: authError } = await authClient.auth.getUser(token);
    if (authError || !authData.user) return json({ error: "Your admin session is invalid. Sign in again." }, 401);

    const adminClient = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
    const { data: adminProfile, error: profileCheckError } = await adminClient.from("profiles").select("role").eq("id", authData.user.id).maybeSingle();
    if (profileCheckError || adminProfile?.role !== "admin") return json({ error: "Administrator permission required." }, 403);

    const body = await req.json();
    const full_name = String(body.full_name || "").trim();
    const registration_id = String(body.registration_id || "").trim();
    const email = String(body.email || "").trim().toLowerCase();
    const password = String(body.password || "");
    if (!full_name || !registration_id || !email || password.length < 8) return json({ error: "Enter a name, registration ID, valid email and password of at least 8 characters." }, 400);
    if (full_name.length > 100 || registration_id.length > 40 || email.length > 254) return json({ error: "One or more fields are too long." }, 400);

    const { data: created, error: createError } = await adminClient.auth.admin.createUser({
      email,
      password,
      email_confirm: true,
      user_metadata: { full_name, registration_id }
    });
    if (createError || !created.user) return json({ error: createError?.message || "Supabase could not create this account." }, 400);

    const { error: saveProfileError } = await adminClient.from("profiles").upsert({
      id: created.user.id,
      full_name,
      registration_id,
      role: "student",
      status: "approved"
    }, { onConflict: "id" });

    if (saveProfileError) {
      await adminClient.auth.admin.deleteUser(created.user.id);
      return json({ error: "Auth account could not be linked to the student profile: " + saveProfileError.message }, 400);
    }
    return json({ success: true, user_id: created.user.id });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "Unexpected server error." }, 500);
  }
});
