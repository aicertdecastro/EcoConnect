// EcoConnect: classify one photographed waste item with Gemini.
//
// POST { image: <base64 JPEG/PNG/WebP>, mimeType: "image/jpeg" }
//   Authorization: Bearer <signed-in user's access token>   (supabase-kt sends it)
// → 200 { category, itemName, confidence, reason, model, latencyMs }
//
// Secrets (never in the app):  supabase secrets set GEMINI_API_KEY=...
// Optional:                    supabase secrets set GEMINI_MODEL=<model to try first>
// The photo is only forwarded to Gemini; it is never stored or logged.

import { createClient } from "jsr:@supabase/supabase-js@2";

const CATEGORIES = ["BIODEGRADABLE", "RECYCLABLE", "RESIDUAL", "SPECIAL", "UNSURE"] as const;
const MAX_BASE64_CHARS = 2_000_000; // ≈1.5 MB of image bytes
const ALLOWED_MIME = new Set(["image/jpeg", "image/png", "image/webp"]);
// Tried in order. Gemini models are often "busy" (503, answered in ~2 s) or slow, so a scan
// moves on to the next model instead of failing. 3.5-flash-lite was the most reliable in tests.
const MODELS = ["gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.1-flash-lite"];
// Each model gets up to 10 s; the last one gets whatever is left of 34 s, so the answer
// still arrives before the app gives up (40 s, including the photo upload).
const ATTEMPT_TIMEOUT_MS = 10_000;
const TOTAL_BUDGET_MS = 34_000;

const PROMPT = `You are a waste segregation assistant for a barangay in Batangas City, Philippines
(RA 9003 categories). Look at the ONE main item in the photo and classify it:
- BIODEGRADABLE: food scraps, fruit/vegetable peels, leaves, garden waste, plain paper napkins.
- RECYCLABLE: clean PET/HDPE plastic bottles and containers, cans, glass bottles, cardboard, clean paper.
- RESIDUAL: items that cannot be composted or recycled: sachets, candy wrappers, styrofoam,
  soiled/greasy packaging, diapers, cigarette butts, multilayer packaging.
- SPECIAL: hazardous or special handling: batteries, bulbs, electronics, paint, chemicals,
  medicine, sharps, aerosol cans.
- UNSURE: the photo is unclear, shows no waste item, or shows several different items.
Return itemName as a short everyday name (e.g. "Plastic bottle"), confidence from 0 to 1,
and reason as one short sentence a resident would understand. Never invent other categories.`;

const RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    category: { type: "STRING", enum: [...CATEGORIES] },
    itemName: { type: "STRING" },
    confidence: { type: "NUMBER" },
    reason: { type: "STRING" },
  },
  required: ["category", "itemName", "confidence", "reason"],
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Use POST" });

  // 1. Only signed-in EcoConnect users.
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  if (!token) return json(401, { error: "Sign in to use the scanner" });
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!);
  const { data: userData, error: userError } = await supabase.auth.getUser(token);
  if (userError || !userData?.user) return json(401, { error: "Sign in to use the scanner" });

  // 2. Validate input.
  let image: string, mimeType: string;
  try {
    ({ image, mimeType } = await req.json());
  } catch {
    return json(400, { error: "Body must be JSON" });
  }
  if (typeof image !== "string" || image.length === 0) return json(400, { error: "Missing image" });
  if (image.length > MAX_BASE64_CHARS) return json(413, { error: "Photo is too large" });
  if (!ALLOWED_MIME.has(mimeType)) return json(400, { error: "Unsupported image type" });

  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) return json(500, { error: "Scanner is not configured" });
  // Optional GEMINI_MODEL secret: try that model first, then the usual list.
  const override = Deno.env.get("GEMINI_MODEL");
  const models = override ? [override, ...MODELS.filter((m) => m !== override)] : MODELS;
  const requestBody = JSON.stringify({
    contents: [{ parts: [{ inline_data: { mime_type: mimeType, data: image } }, { text: PROMPT }] }],
    generationConfig: {
      temperature: 0.1,
      responseMimeType: "application/json",
      responseSchema: RESPONSE_SCHEMA,
    },
  });

  // 3. Ask Gemini for structured JSON. A slow, overloaded (429/5xx) or unreachable model
  //    is skipped and the next model is tried.
  const started = Date.now();
  let geminiResponse: Response | null = null;
  let model = models[0];
  for (const candidate of models) {
    model = candidate;
    const isLast = candidate === models[models.length - 1];
    const remaining = TOTAL_BUDGET_MS - (Date.now() - started);
    const timeoutMs = isLast ? remaining : Math.min(ATTEMPT_TIMEOUT_MS, remaining);
    if (timeoutMs < 1_000) break;
    try {
      geminiResponse = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${candidate}:generateContent`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
          signal: AbortSignal.timeout(timeoutMs),
          body: requestBody,
        },
      );
    } catch (e) {
      console.error("gemini fetch failed", candidate, (e as Error).name, `${Date.now() - started}ms`);
      geminiResponse = null;
      continue;
    }
    const retryable = geminiResponse.status === 429 || geminiResponse.status >= 500;
    if (!retryable || isLast) break;
    console.error("gemini busy, trying next model", candidate, geminiResponse.status);
    await geminiResponse.body?.cancel();
    geminiResponse = null;
  }
  if (!geminiResponse) {
    return json(504, { error: "The AI service did not respond. Try again." });
  }
  const latencyMs = Date.now() - started;

  if (!geminiResponse.ok) {
    // Log Gemini's status and error message only: never the key or the image.
    let detail = "";
    try {
      detail = (await geminiResponse.json())?.error?.message ?? "";
    } catch { /* not JSON */ }
    console.error("gemini error", geminiResponse.status, model, detail.slice(0, 300));
    const status = geminiResponse.status === 429 ? 429 : 502;
    return json(status, {
      error: status === 429 ? "Too many scans right now. Wait a moment." : "The AI service returned an error.",
    });
  }

  // 4. Parse and sanitise the model output.
  try {
    const payload = await geminiResponse.json();
    const candidate = payload?.candidates?.[0];
    // Newer models may split the answer into several parts (e.g. a "thought" part first),
    // so join every non-thought text part instead of reading only parts[0].
    const text: string = (candidate?.content?.parts ?? [])
      .filter((p: { text?: string; thought?: boolean }) => typeof p.text === "string" && !p.thought)
      .map((p: { text: string }) => p.text)
      .join("")
      .trim()
      .replace(/^```(?:json)?\s*/i, "")
      .replace(/\s*```$/, "");
    if (!text) {
      console.error("gemini returned no text", model, candidate?.finishReason, payload?.promptFeedback?.blockReason);
      return json(502, { error: "The AI gave no answer for this photo. Try again." });
    }
    const parsed = JSON.parse(text);
    const category = CATEGORIES.includes(parsed.category) ? parsed.category : "UNSURE";
    const confidence = Math.max(0, Math.min(1, Number(parsed.confidence) || 0));
    return json(200, {
      category,
      itemName: String(parsed.itemName ?? "").slice(0, 80),
      confidence,
      reason: String(parsed.reason ?? "").slice(0, 300),
      model,
      latencyMs,
    });
  } catch {
    console.error("gemini returned unparseable output", model);
    return json(502, { error: "The AI answer could not be read. Try again." });
  }
});
