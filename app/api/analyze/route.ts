import OpenAI from "openai";
import { createClient as createSupabaseClient } from "@/lib/supabase/server";

export const runtime = "nodejs";

const SYSTEM_PROMPT = `
You are a crypto futures chart-analysis assistant for a Korean-language trading support app.

Analyze ONE uploaded chart screenshot together with the user's inputs:
- capital in USD
- risk level: aggressive / normal / conservative
- leverage
- symbol: BTC or ETH
- timeframe: 1m / 15m / 1h

Rules:
1. This is a support tool, not financial advice.
2. Base your answer only on the visible chart and provided inputs.
3. Do not invent invisible indicators, orderbook data, funding data, or news.
4. For normal or conservative risk, if the chart is unclear, confidence is low, or a good setup is not visible, return WAIT.
5. For aggressive risk, do NOT return WAIT unless the chart is completely unreadable. Choose LONG or SHORT based on the stronger visible directional bias.
6. If aggressive mode forces a weak setup, lower confidence and lower size_percent accordingly.
7. Return Korean text except enum values LONG, SHORT, WAIT.
8. Be conservative with size_percent even in aggressive mode.
9. Use clear numeric outputs where possible.
10. If entry is not appropriate, set entry to null and size_percent to 0.
11. Reasons must be concise and tied to what is visible on the chart.
12. warning must explain when the idea becomes invalid.
13. confidence must be an integer from 0 to 100.
14. Never promise profit or certainty.
`;

const ANALYSIS_SCHEMA = {
  type: "json_schema",
  name: "scalping_analysis",
  strict: true,
  schema: {
    type: "object",
    additionalProperties: false,
    properties: {
      symbol: {
        type: "string",
        enum: ["BTC", "ETH"],
      },
      timeframe: {
        type: "string",
        enum: ["1m", "15m", "1h"],
      },
      position: {
        type: "string",
        enum: ["LONG", "SHORT", "WAIT"],
      },
      entry: {
        anyOf: [
          {
            type: "object",
            additionalProperties: false,
            properties: {
              type: {
                type: "string",
                enum: ["range"],
              },
              min: { type: "number" },
              max: { type: "number" },
            },
            required: ["type", "min", "max"],
          },
          { type: "null" },
        ],
      },
      stop_loss: {
        anyOf: [{ type: "number" }, { type: "null" }],
      },
      take_profit: {
        type: "array",
        items: { type: "number" },
        maxItems: 2,
      },
      size_percent: { type: "number" },
      confidence: { type: "integer", minimum: 0, maximum: 100 },
      reasons: {
        type: "array",
        items: { type: "string" },
        minItems: 3,
        maxItems: 4,
      },
      warning: { type: "string" },
    },
    required: [
      "symbol",
      "timeframe",
      "position",
      "entry",
      "stop_loss",
      "take_profit",
      "size_percent",
      "confidence",
      "reasons",
      "warning",
    ],
  },
} as const;

function fileToDataUrl(buffer: Buffer, mimeType: string) {
  return `data:${mimeType};base64,${buffer.toString("base64")}`;
}

export async function POST(req: Request) {
  try {
    const supabase = await createSupabaseClient();
    const {
      data: { user },
    } = await supabase.auth.getUser();

    if (!user) {
      return Response.json(
        { error: "로그인이 필요합니다." },
        { status: 401 }
      );
    }

    const apiKey = process.env.OPENAI_API_KEY;

    if (!apiKey) {
      return Response.json(
        { error: "서버가 OPENAI_API_KEY를 읽지 못했습니다." },
        { status: 500 }
      );
    }

    const openai = new OpenAI({ apiKey });

    const formData = await req.formData();

    const capital = String(formData.get("capital") || "");
    const riskLevel = String(formData.get("riskLevel") || "");
    const leverage = String(formData.get("leverage") || "");
    const symbol = String(formData.get("symbol") || "");
    const timeframe = String(formData.get("timeframe") || "");
    const image = formData.get("image") as File | null;

    if (!image) {
      return Response.json(
        { error: "차트 이미지가 없습니다." },
        { status: 400 }
      );
    }

    const arrayBuffer = await image.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);
    const imageDataUrl = fileToDataUrl(buffer, image.type || "image/png");

    const timeframeMap: Record<string, "1m" | "15m" | "1h"> = {
      "1분봉": "1m",
      "15분봉": "15m",
      "1시간봉": "1h",
      "1m": "1m",
      "15m": "15m",
      "1h": "1h",
    };

    const normalizedTimeframe = timeframeMap[timeframe] || "15m";
    const normalizedRisk =
      riskLevel === "공격적"
        ? "aggressive"
        : riskLevel === "보수적"
          ? "conservative"
          : "normal";

    const userPrompt = `
아래 조건을 바탕으로 차트 이미지를 분석하라.

입력 조건:
- 자본(USD): ${capital}
- 리스크 성향: ${normalizedRisk}
- 레버리지: ${leverage}배
- 종목: ${symbol}
- 타임프레임: ${normalizedTimeframe}

반환 규칙:
- 가능한 값은 LONG, SHORT, WAIT 중 하나
- 리스크 성향이 normal 또는 conservative이면 차트가 불명확하거나 기대 손익비가 낮을 때 WAIT
- 리스크 성향이 aggressive이면 차트가 완전히 판독 불가한 경우를 제외하고 WAIT를 사용하지 말고 LONG 또는 SHORT 중 하나를 반환
- aggressive에서 억지 포지션일 경우 confidence를 낮게 주고 size_percent도 낮게 제시
- confidence는 반드시 0~100 사이의 정수
- 진입, 손절, 익절, 비중, 신뢰도, 근거, 경고를 JSON으로 반환
- 숫자는 가능한 한 명확하게 제시
- 근거는 3~4개
- 한국어로 작성
`;

    const response = await openai.responses.create({
      model: "gpt-5-mini",
      store: false,
      input: [
        {
          role: "system",
          content: [{ type: "input_text", text: SYSTEM_PROMPT }],
        },
        {
          role: "user",
          content: [
            { type: "input_text", text: userPrompt },
            {
              type: "input_image",
              image_url: imageDataUrl,
            },
          ],
        },
      ],
      text: {
        format: ANALYSIS_SCHEMA,
      },
    });

    const outputText = response.output_text;

    if (!outputText) {
      return Response.json(
        { error: "OpenAI 응답이 비어 있습니다." },
        { status: 500 }
      );
    }

    const parsed = JSON.parse(outputText);

    return Response.json(parsed);
  } catch (error) {
    console.error("analyze error:", error);

    if (error instanceof Error) {
      return Response.json(
        { error: `분석 중 오류: ${error.message}` },
        { status: 500 }
      );
    }

    return Response.json(
      { error: "분석 중 알 수 없는 오류가 발생했습니다." },
      { status: 500 }
    );
  }
}