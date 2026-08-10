"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useAuth } from "@/hooks/useAuth";
import { Sparkles, Eye, EyeOff, Loader2 } from "lucide-react";

const registerSchema = z.object({
  username: z.string().min(3, "用户名至少 3 个字符"),
  email: z.string().email("请输入有效的邮箱地址"),
  password: z.string().min(6, "密码至少 6 个字符"),
  confirmPassword: z.string().min(6, "确认密码至少 6 个字符"),
}).refine((data) => data.password === data.confirmPassword, {
  message: "两次输入的密码不一致",
  path: ["confirmPassword"],
});

type RegisterForm = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const router = useRouter();
  const { register, isLoading, error } = useAuth();
  const [showPassword, setShowPassword] = React.useState(false);

  const {
    register: registerField,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    defaultValues: { username: "", email: "", password: "", confirmPassword: "" },
  });

  const onSubmit = async (data: RegisterForm) => {
    try {
      await register({
        username: data.username,
        email: data.email,
        password: data.password,
      });
      router.push("/dashboard");
    } catch {
      // Error handled by hook
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-brand-50 via-white to-accent-50 dark:from-brand-950/30 dark:via-gray-900 dark:to-accent-950/30 p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-accent-500 shadow-lg shadow-brand-500/25 mb-4">
            <Sparkles className="h-8 w-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold bg-gradient-to-r from-brand-600 to-accent-600 bg-clip-text text-transparent">
            FocusOS AI
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            创建你的专属账户
          </p>
        </div>

        <div className="rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 p-8 shadow-xl">
          <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-6 text-center">
            注册新账号
          </h2>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <Input label="用户名" placeholder="设置你的用户名" error={errors.username?.message} {...registerField("username")} />
            <Input label="邮箱" type="email" placeholder="your@email.com" error={errors.email?.message} {...registerField("email")} />

            <div className="relative">
              <Input label="密码" type={showPassword ? "text" : "password"} placeholder="至少 6 位" error={errors.password?.message} {...registerField("password")} />
              <button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-[34px] text-gray-400 hover:text-gray-600">
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>

            <Input label="确认密码" type={showPassword ? "text" : "password"} placeholder="再次输入密码" error={errors.confirmPassword?.message} {...registerField("confirmPassword")} />

            {error && (
              <p className="text-sm text-red-500 text-center">{error.message}</p>
            )}

            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              {isLoading ? "注册中..." : "创建账号"}
            </Button>
          </form>

          <div className="mt-6 text-center text-sm text-gray-500 dark:text-gray-400">
            <span>已有账号？</span>
            <Link href="/login" className="text-brand-600 dark:text-brand-400 font-medium hover:underline ml-1">
              立即登录
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
