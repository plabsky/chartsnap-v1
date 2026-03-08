"use client";

import { useState } from "react";
import { createClient } from "@/lib/supabase/client";

export default function LoginPage() {
  const supabase = createClient();

  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const handleLogin = async () => {
    setMessage("");
    setError("");

    if (!email) {
      setError("이메일을 입력해주세요.");
      return;
    }

    try {
      setLoading(true);

      const { error } = await supabase.auth.signInWithOtp({
        email,
        options: {
          emailRedirectTo: `${window.location.origin}/auth/callback`,
          shouldCreateUser: false,
        },
      });

      if (error) {
        throw error;
      }

      setMessage(
        "로그인 링크를 전송했습니다. 허용된 이메일이면 메일함에서 링크를 확인해주세요."
      );
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "로그인 요청 중 오류가 발생했습니다.";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen bg-[#05070b] text-white">
      <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6">
        <div className="rounded-[28px] border border-emerald-500/30 bg-[#070b11] p-7 shadow-[0_0_40px_rgba(34,197,94,0.08)]">
          <p className="text-sm font-medium text-emerald-400">ChartSnap v1.0</p>
          <h1 className="mt-2 text-4xl font-extrabold tracking-tight text-emerald-400">
            Crypto Scalping
          </h1>
          <p className="mt-3 text-sm leading-7 text-zinc-400">
            허용된 이메일만 로그인할 수 있습니다. 메일로 받은 링크를 눌러 접속하세요.
          </p>

          <div className="mt-6">
            <label className="mb-2 block text-sm font-semibold text-emerald-300">
              이메일
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className="w-full rounded-2xl border border-zinc-800 bg-[#0a0f16] px-4 py-3 text-white outline-none placeholder:text-zinc-500 transition focus:border-emerald-500"
            />
          </div>

          <button
            type="button"
            onClick={handleLogin}
            disabled={loading}
            className="mt-6 w-full rounded-2xl bg-emerald-500 px-4 py-4 text-lg font-bold text-black shadow-[0_0_25px_rgba(34,197,94,0.22)] transition hover:bg-emerald-400 disabled:cursor-not-allowed disabled:bg-emerald-300"
          >
            {loading ? "전송 중..." : "로그인 링크 받기"}
          </button>

          {message ? (
            <div className="mt-4 rounded-2xl border border-emerald-900 bg-emerald-950/30 p-4 text-sm text-emerald-300">
              {message}
            </div>
          ) : null}

          {error ? (
            <div className="mt-4 rounded-2xl border border-red-900 bg-red-950/40 p-4 text-sm text-red-300">
              {error}
            </div>
          ) : null}
        </div>
      </div>
    </main>
  );
}