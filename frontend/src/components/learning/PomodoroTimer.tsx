"use client";

import * as React from "react";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Play, Pause, RotateCcw, Coffee, Brain } from "lucide-react";

type TimerMode = "focus" | "short-break" | "long-break";

const modeConfig: Record<TimerMode, { duration: number; label: string; icon: typeof Brain }> = {
  focus: { duration: 25 * 60, label: "专注", icon: Brain },
  "short-break": { duration: 5 * 60, label: "短休", icon: Coffee },
  "long-break": { duration: 15 * 60, label: "长休", icon: Coffee },
};

export function PomodoroTimer() {
  const [mode, setMode] = React.useState<TimerMode>("focus");
  const [timeLeft, setTimeLeft] = React.useState(modeConfig.focus.duration);
  const [isRunning, setIsRunning] = React.useState(false);

  React.useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isRunning && timeLeft > 0) {
      interval = setInterval(() => {
        setTimeLeft((prev) => prev - 1);
      }, 1000);
    } else if (timeLeft === 0) {
      setIsRunning(false);
    }
    return () => clearInterval(interval);
  }, [isRunning, timeLeft]);

  const minutes = Math.floor(timeLeft / 60);
  const seconds = timeLeft % 60;
  const progress = 1 - timeLeft / modeConfig[mode].duration;

  const handleStart = () => setIsRunning(true);
  const handlePause = () => setIsRunning(false);
  const handleReset = () => {
    setIsRunning(false);
    setTimeLeft(modeConfig[mode].duration);
  };
  const handleModeChange = (newMode: TimerMode) => {
    setMode(newMode);
    setIsRunning(false);
    setTimeLeft(modeConfig[newMode].duration);
  };

  const radius = 80;
  const circumference = radius * 2 * Math.PI;
  const strokeDashoffset = circumference - progress * circumference;

  return (
    <Card className="bg-gradient-to-br from-brand-50 via-white to-accent-50 dark:from-brand-950/20 dark:via-gray-900 dark:to-accent-950/20">
      <CardContent className="p-6">
        <div className="flex items-center justify-center gap-2 mb-6">
          {(Object.keys(modeConfig) as TimerMode[]).map((m) => (
            <Button
              key={m}
              variant={mode === m ? "default" : "outline"}
              size="sm"
              onClick={() => handleModeChange(m)}
            >
              {modeConfig[m].label}
            </Button>
          ))}
        </div>

        <div className="flex items-center justify-center mb-6">
          <div className="relative">
            <svg width="200" height="200" className="-rotate-90 transform">
              <circle
                cx="100"
                cy="100"
                r={radius}
                strokeWidth="8"
                className="fill-none stroke-gray-200 dark:stroke-gray-800"
              />
              <circle
                cx="100"
                cy="100"
                r={radius}
                strokeWidth="8"
                strokeDasharray={circumference}
                strokeDashoffset={strokeDashoffset}
                strokeLinecap="round"
                className="fill-none stroke-brand-500 transition-all duration-300"
              />
            </svg>
            <div className="absolute inset-0 flex items-center justify-center">
              <span className="text-5xl font-bold text-gray-900 dark:text-white tabular-nums">
                {String(minutes).padStart(2, "0")}:{String(seconds).padStart(2, "0")}
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-center gap-3">
          {isRunning ? (
            <Button variant="destructive" size="lg" onClick={handlePause}>
              <Pause className="mr-2 h-5 w-5" />
              暂停
            </Button>
          ) : (
            <Button variant="default" size="lg" onClick={handleStart}>
              <Play className="mr-2 h-5 w-5" />
              开始
            </Button>
          )}
          <Button variant="outline" size="lg" onClick={handleReset}>
            <RotateCcw className="mr-2 h-5 w-5" />
            重置
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}