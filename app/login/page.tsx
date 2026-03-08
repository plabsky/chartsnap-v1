import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import AnalyzerClient from "./analyzer-client";

export default async function HomePage() {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) {
    redirect("/login");
  }

  return <AnalyzerClient />;
}