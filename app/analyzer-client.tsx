"use client";

import { useMemo, useState } from "react";
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
      ? "LONG (매수)"
      : result?.position === "SHORT"
        ? "SHORT (매도)"
        : result?.position === "WAIT"
          ? "WAIT (관망)"
          : "-";

  const theme = useMemo(() => {
    if (!result) {
      return {
        heroBorder: "border-emerald-500/50",
        heroGlow: "shadow-[0_0_35px_rgba(34,197,94,0.14)]",
        heroText: "text-emerald-400",
        chipText: "text-emerald-300",
      };
    }

    if (result.position === "LONG") {
      return {
        heroBorder: "border-emerald-500/60",
        heroGlow: "shadow-[0_0_35px_rgba(34,197,94,0.16)]",
        heroText: "text-emerald-400",
        chipText: "text-emerald-300",
      };
    }

    if (result.position === "SHORT") {
      return {
        heroBorder: "border-red-500/60",
        heroGlow: "shadow-[0_0_35px_rgba(239,68,68,0.14)]",
        heroText: "text-red-400",
        chipText: "text-red-300",
      };
    }

    return {
      heroBorder: "border-amber-500/60",
      heroGlow: "shadow-[0_0_35px_rgba(245,158,11,0.14)]",
      heroText: "text-amber-400",
      chipText: "text-amber-300",
    };
  }, [result]);

  const riskButtonClass = (value: string) =>
    riskLevel === value
      ? "border-emerald-400 bg-emerald-500/10 text-emerald-300 shadow-[0_0_20px_rgba(34,197,94,0.12)]"
      : "border-zinc-800 bg-zinc-950 text-zinc-300 hover:border-emerald-500/40 hover:text-emerald-300";

  return (
    <main className="min-h-screen bg-[#05070b] text-white">
      <div className="mx-auto flex min-h-screen w-full max-w-6xl flex-col px-6 py-10">
        <div className="mb-8 flex items-start justify-between gap-4">
          <div>
            <p className="text-sm font-medium text-emerald-400">ChartSnap v1.0</p>
            <h1 className="mt-2 text-4xl font-extrabold tracking-tight text-emerald-400 md:text-5xl">
              Crypto Scalping
            </h1>
            <p className="mt-3 text-base text-zinc-400">
              AI 트레이딩 어시스턴트로 차트를 분석하세요
            </p>
          </div>

          <button
            type="button"
            onClick={handleLogout}
            disabled={loggingOut}
            className="rounded-xl border border-zinc-700 bg-transparent px-4 py-2 text-sm font-semibold text-zinc-200 transition hover:border-emerald-400 hover:text-emerald-300 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loggingOut ? "로그아웃 중..." : "로그아웃"}
          </button>
        </div>

        {!result ? (
          <div className="rounded-[28px] border border-emerald-500/30 bg-[#070b11] p-6 shadow-[0_0_40px_rgba(34,197,94,0.08)]">
            <div className="mb-5">
              <h2 className="text-2xl font-bold text-white">거래 조건 설정</h2>
              <p className="mt-2 text-zinc-400">
                현재 포지션과 분석할 차트 이미지를 업로드해주세요.
              </p>
            </div>

            <div className="grid gap-6 lg:grid-cols-[1.1fr_1fr]">
              <div className="space-y-5">
                <div className="grid gap-5 md:grid-cols-2">
                  <div>
                    <label className="mb-2 block text-sm font-semibold text-emerald-300">
                      종목
                    </label>
                    <select
                      value={symbol}
                      onChange={(e) => setSymbol(e.target.value)}
                      className="w-full rounded-2xl border border-zinc-800 bg-[#0a0f16] px-4 py-3 text-white outline-none transition focus:border-emerald-500"
                    >
                      <option value="BTC">BTC (Bitcoin)</option>
                      <option value="ETH">ETH (Ethereum)</option>
                    </select>
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-semibold text-emerald-300">
                      타임프레임
                    </label>
                    <select
                      value={timeframe}
                      onChange={(e) => setTimeframe(e.target.value)}
                      className="w-full rounded-2xl border border-zinc-800 bg-[#0a0f16] px-4 py-3 text-white outline-none transition focus:border-emerald-500"
                    >
                      <option value="1분봉">1분봉 (1m)</option>
                      <option value="15분봉">15분봉 (15m)</option>
                      <option value="1시간봉">1시간봉 (1h)</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="mb-2 block text-sm font-semibold text-emerald-300">
                    투자금 (USD)
                  </label>
                  <input
                    type="number"
                    value={capital}
                    onChange={(e) => setCapital(e.target.value)}
                    placeholder="예: 1000"
                    className="w-full rounded-2xl border border-zinc-800 bg-[#0a0f16] px-4 py-3 text-white outline-none placeholder:text-zinc-500 transition focus:border-emerald-500"
                  />
                </div>

                <div>
                  <label className="mb-2 block text-sm font-semibold text-emerald-300">
                    레버리지
                  </label>
                  <select
                    value={leverage}
                    onChange={(e) => setLeverage(e.target.value)}
                    className="w-full rounded-2xl border border-zinc-800 bg-[#0a0f16] px-4 py-3 text-white outline-none transition focus:border-emerald-500"
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
                  <label className="mb-3 block text-sm font-semibold text-emerald-300">
                    리스크 성향
                  </label>
                  <div className="grid grid-cols-3 gap-3">
                    <button
                      type="button"
                      onClick={() => setRiskLevel("보수적")}
                      className={`rounded-2xl border px-4 py-3 text-sm font-bold transition ${riskButtonClass("보수적")}`}
                    >
                      보수적
                    </button>
                    <button
                      type="button"
                      onClick={() => setRiskLevel("기본")}
                      className={`rounded-2xl border px-4 py-3 text-sm font-bold transition ${riskButtonClass("기본")}`}
                    >
                      일반
                    </button>
                    <button
                      type="button"
                      onClick={() => setRiskLevel("공격적")}
                      className={`rounded-2xl border px-4 py-3 text-sm font-bold transition ${riskButtonClass("공격적")}`}
                    >
                      공격적
                    </button>
                  </div>
                </div>
              </div>

              <div>
                <label className="mb-2 block text-sm font-semibold text-emerald-300">
                  차트 이미지
                </label>
                <label className="flex min-h-[320px] cursor-pointer flex-col items-center justify-center rounded-[28px] border border-dashed border-zinc-700 bg-[#080d13] p-6 text-center transition hover:border-emerald-500/50 hover:bg-[#0b1119]">
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
                  <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-500/10 text-3xl text-emerald-400">
                    ⤴
                  </div>
                  <p className="text-xl font-bold text-white">차트 스크린샷 업로드</p>
                  <p className="mt-2 text-sm text-zinc-400">
                    클릭하거나 드래그하여 파일 선택
                  </p>
                  {imageName ? (
                    <div className="mt-5 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-4 py-2 text-sm font-medium text-emerald-300">
                      {imageName}
                    </div>
                  ) : null}
                </label>
              </div>
            </div>

            <button
              type="button"
              onClick={handleAnalyze}
              disabled={loading}
              className="mt-8 w-full rounded-2xl bg-emerald-500 px-4 py-4 text-lg font-bold text-black shadow-[0_0_25px_rgba(34,197,94,0.22)] transition hover:bg-emerald-400 disabled:cursor-not-allowed disabled:bg-emerald-300"
            >
              {loading ? "분석 중..." : "분석 시작 →"}
            </button>

            {error ? (
              <div className="mt-4 rounded-2xl border border-red-900 bg-red-950/40 p-4 text-sm text-red-300">
                {error}
              </div>
            ) : null}
          </div>
        ) : (
          <div className="space-y-6">
            <div className={`rounded-[28px] border bg-[#070b11] p-6 ${theme.heroBorder} ${theme.heroGlow}`}>
              <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">
                <div>
                  <p className="text-sm font-semibold text-zinc-400">추천 포지션</p>
                  <h2 className={`mt-2 text-4xl font-extrabold md:text-5xl ${theme.heroText}`}>
                    {positionLabel}
                  </h2>
                </div>

                <div className="grid grid-cols-2 gap-0 overflow-hidden rounded-2xl border border-zinc-800 bg-[#0a0f16]">
                  <div className="min-w-[120px] px-6 py-5 text-center">
                    <p className="text-sm text-zinc-500">신뢰도</p>
                    <p className={`mt-2 text-3xl font-extrabold ${theme.chipText}`}>
                      {result.confidence}
                    </p>
                  </div>
                  <div className="min-w-[120px] border-l border-zinc-800 px-6 py-5 text-center">
                    <p className="text-sm text-zinc-500">권장 비중</p>
                    <p className="mt-2 text-3xl font-extrabold text-white">
                      {result.size_percent}%
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div className="grid gap-5 md:grid-cols-3">
              <div className="rounded-[24px] border border-zinc-800 bg-[#070b11] p-6">
                <p className="text-sm font-semibold text-zinc-400">진입 구간</p>
                <p className="mt-4 text-4xl font-extrabold leading-tight text-white">
                  {result.entry ? (
                    <>
                      {result.entry.min.toLocaleString()} -<br />
                      {result.entry.max.toLocaleString()}
                    </>
                  ) : (
                    "-"
                  )}
                </p>
              </div>

              <div className="rounded-[24px] border border-emerald-900 bg-emerald-950/20 p-6">
                <p className="text-sm font-semibold text-emerald-300">익절가 (Take Profit)</p>
                <p className="mt-4 text-4xl font-extrabold leading-tight text-emerald-400">
                  {result.take_profit.length > 0
                    ? result.take_profit.join(" / ")
                    : "-"}
                </p>
              </div>

              <div className="rounded-[24px] border border-red-900 bg-red-950/20 p-6">
                <p className="text-sm font-semibold text-red-300">손절가 (Stop Loss)</p>
                <p className="mt-4 text-4xl font-extrabold leading-tight text-red-400">
                  {result.stop_loss ?? "-"}
                </p>
              </div>
            </div>

            <div className="grid gap-5 md:grid-cols-2">
              <div className="rounded-[24px] border border-zinc-800 bg-[#070b11] p-6">
                <p className="text-2xl font-bold text-emerald-400">분석 근거</p>
                <ul className="mt-5 space-y-4 text-base text-zinc-200">
                  {result.reasons.map((reason, index) => (
                    <li key={index} className="flex items-start gap-3">
                      <span className="mt-[6px] h-2 w-2 rounded-full bg-emerald-400" />
                      <span>{reason}</span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="rounded-[24px] border border-amber-700 bg-amber-950/20 p-6">
                <p className="text-2xl font-bold text-amber-400">경고 및 주의사항</p>
                <p className="mt-5 text-base leading-8 text-amber-200/90">
                  {result.warning}
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={() => {
                setResult(null);
                setError("");
              }}
              className="ml-auto rounded-2xl border border-zinc-700 bg-transparent px-5 py-3 text-sm font-semibold text-white transition hover:border-emerald-400 hover:text-emerald-300"
            >
              새로운 분석
            </button>
          </div>
        )}
      </div>
    </main>
  );
}