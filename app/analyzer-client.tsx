"use client";

import { useState } from "react";
import { createClient } from "@/lib/supabase/client";

type AnalysisResult = {
  symbol: "BTC" | "ETH";
  timeframe: "1m" | "15m" | "1h";
  position: "LONG" | "SHORT" | "WAIT";
  entry:
    | {
        type: "range";
        min: number;
        max: number;
      }
    | null;
  stop_loss: number | null;
  take_profit: number[];
  size_percent: number;
  confidence: number;
  reasons: string[];
  warning: string;
};

export default function AnalyzerClient() {
  const supabase = createClient();

  const [capital, setCapital] = useState("");
  const [riskLevel, setRiskLevel] = useState("기본");
  const [leverage, setLeverage] = useState("5");
  const [symbol, setSymbol] = useState("BTC");
  const [timeframe, setTimeframe] = useState("15분봉");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageName, setImageName] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<AnalysisResult | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);

  const handleAnalyze = async () => {
    setError("");
    setResult(null);

    if (!capital) {
      setError("자본을 입력해주세요.");
      return;
    }

    if (!imageFile) {
      setError("차트 이미지를 업로드해주세요.");
      return;
    }

    try {
      setLoading(true);

      const formData = new FormData();
      formData.append("capital", capital);
      formData.append("riskLevel", riskLevel);
      formData.append("leverage", leverage);
      formData.append("symbol", symbol);
      formData.append("timeframe", timeframe);
      formData.append("image", imageFile);

      const res = await fetch("/api/analyze", {
        method: "POST",
        body: formData,
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.error || "분석 요청에 실패했습니다.");
      }

      setResult(data);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "알 수 없는 오류가 발생했습니다.";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      setLoggingOut(true);
      const { error } = await supabase.auth.signOut();

      if (error) {
        throw error;
      }

      window.location.href = "/login";
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "로그아웃 중 오류가 발생했습니다.";
      setError(message);
      setLoggingOut(false);
    }
  };

  const positionLabel =
    result?.position === "LONG"
      ? "롱"
      : result?.position === "SHORT"
        ? "숏"
        : result?.position === "WAIT"
          ? "관망"
          : "-";

  return (
    <main className="min-h-screen bg-black text-white">
      <div className="mx-auto flex min-h-screen w-full max-w-4xl flex-col px-6 py-10">
        <div className="mb-8 flex items-start justify-between gap-4">
          <div>
            <p className="text-sm text-zinc-400">ChartSnap v1.0</p>
            <h1 className="mt-2 text-4xl font-bold">바이낸스 단타 분석기</h1>
            <p className="mt-3 text-sm leading-6 text-zinc-400">
              자본, 리스크, 레버리지, 종목, 타임프레임을 입력하고 차트 이미지를
              업로드하면 진입·손절·익절 후보를 분석합니다.
            </p>
          </div>

          <button
            type="button"
            onClick={handleLogout}
            disabled={loggingOut}
            className="rounded-xl border border-zinc-700 px-4 py-2 text-sm font-medium text-white transition hover:bg-zinc-900 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loggingOut ? "로그아웃 중..." : "로그아웃"}
          </button>
        </div>

        <div className="rounded-2xl border border-zinc-800 bg-zinc-950 p-6 shadow-lg">
          <div className="grid gap-5 md:grid-cols-2">
            <div>
              <label className="mb-2 block text-sm font-medium text-zinc-300">
                자본 (USD)
              </label>
              <input
                type="number"
                value={capital}
                onChange={(e) => setCapital(e.target.value)}
                placeholder="예: 1000"
                className="w-full rounded-xl border border-zinc-800 bg-zinc-900 px-4 py-3 text-white outline-none placeholder:text-zinc-500 focus:border-zinc-600"
              />
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-zinc-300">
                리스크 성향
              </label>
              <select
                value={riskLevel}
                onChange={(e) => setRiskLevel(e.target.value)}
                className="w-full rounded-xl border border-zinc-800 bg-zinc-900 px-4 py-3 text-white outline-none focus:border-zinc-600"
              >
                <option value="공격적">공격적</option>
                <option value="기본">기본</option>
                <option value="보수적">보수적</option>
              </select>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-zinc-300">
                레버리지
              </label>
              <select
                value={leverage}
                onChange={(e) => setLeverage(e.target.value)}
                className="w-full rounded-xl border border-zinc-800 bg-zinc-900 px-4 py-3 text-white outline-none focus:border-zinc-600"
              >
                <option value="1">1배</option>
                <option value="2">2배</option>
                <option value="3">3배</option>
                <option value="5">5배</option>
                <option value="10">10배</option>
                <option value="20">20배</option>
              </select>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-zinc-300">
                종목
              </label>
              <select
                value={symbol}
                onChange={(e) => setSymbol(e.target.value)}
                className="w-full rounded-xl border border-zinc-800 bg-zinc-900 px-4 py-3 text-white outline-none focus:border-zinc-600"
              >
                <option value="BTC">BTC</option>
                <option value="ETH">ETH</option>
              </select>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-zinc-300">
                타임프레임
              </label>
              <select
                value={timeframe}
                onChange={(e) => setTimeframe(e.target.value)}
                className="w-full rounded-xl border border-zinc-800 bg-zinc-900 px-4 py-3 text-white outline-none focus:border-zinc-600"
              >
                <option value="1분봉">1분봉</option>
                <option value="15분봉">15분봉</option>
                <option value="1시간봉">1시간봉</option>
              </select>
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-zinc-300">
                차트 이미지 업로드
              </label>
              <label className="flex cursor-pointer items-center justify-center rounded-xl border border-dashed border-zinc-700 bg-zinc-900 px-4 py-3 text-sm text-zinc-300 hover:border-zinc-500">
                <input
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={(e) => {
                    const file = e.target.files?.[0] || null;
                    setImageFile(file);
                    setImageName(file ? file.name : "");
                  }}
                />
                {imageName ? imageName : "이미지 선택"}
              </label>
            </div>
          </div>

          <button
            type="button"
            onClick={handleAnalyze}
            disabled={loading}
            className="mt-8 w-full rounded-xl bg-white px-4 py-3 font-semibold text-black transition hover:bg-zinc-200 disabled:cursor-not-allowed disabled:bg-zinc-400"
          >
            {loading ? "분석 중..." : "분석 시작"}
          </button>

          {error ? (
            <div className="mt-4 rounded-xl border border-red-900 bg-red-950/40 p-4 text-sm text-red-300">
              {error}
            </div>
          ) : null}

          <div className="mt-6 rounded-xl border border-zinc-800 bg-zinc-900/60 p-4 text-sm text-zinc-400">
            본 결과는 투자자문이 아닌 참고용 분석입니다.
          </div>
        </div>

        {result ? (
          <div className="mt-8 grid gap-4">
            <div className="rounded-2xl border border-zinc-800 bg-zinc-950 p-6">
              <h2 className="text-xl font-bold">분석 결과</h2>
              <div className="mt-4 grid gap-4 md:grid-cols-3">
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                  <p className="text-sm text-zinc-400">추천 포지션</p>
                  <p className="mt-2 text-2xl font-bold">{positionLabel}</p>
                </div>
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                  <p className="text-sm text-zinc-400">신뢰도</p>
                  <p className="mt-2 text-2xl font-bold">
                    {result.confidence}점
                  </p>
                </div>
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                  <p className="text-sm text-zinc-400">권장 비중</p>
                  <p className="mt-2 text-2xl font-bold">
                    {result.size_percent}%
                  </p>
                </div>
              </div>

              <div className="mt-4 grid gap-4 md:grid-cols-3">
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                  <p className="text-sm text-zinc-400">진입 구간</p>
                  <p className="mt-2 text-lg font-semibold">
                    {result.entry
                      ? `${result.entry.min} ~ ${result.entry.max}`
                      : "-"}
                  </p>
                </div>
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                  <p className="text-sm text-zinc-400">손절가</p>
                  <p className="mt-2 text-lg font-semibold">
                    {result.stop_loss ?? "-"}
                  </p>
                </div>
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                  <p className="text-sm text-zinc-400">익절가</p>
                  <p className="mt-2 text-lg font-semibold">
                    {result.take_profit.length > 0
                      ? result.take_profit.join(" / ")
                      : "-"}
                  </p>
                </div>
              </div>

              <div className="mt-4 rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                <p className="text-sm text-zinc-400">근거</p>
                <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-zinc-200">
                  {result.reasons.map((reason, index) => (
                    <li key={index}>{reason}</li>
                  ))}
                </ul>
              </div>

              <div className="mt-4 rounded-xl border border-yellow-900 bg-yellow-950/30 p-4 text-sm text-yellow-200">
                {result.warning}
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </main>
  );
}