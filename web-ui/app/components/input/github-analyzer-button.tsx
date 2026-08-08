import * as React from "react";

import { Github, LoaderCircle, Search, ArrowRight } from "lucide-react";
import { useTranslation } from "react-i18next";

import { cn } from "~/lib/utils";
import { Button } from "~/components/ui/button";
import { Input } from "~/components/ui/input";
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from "~/components/ui/popover";

export interface GitHubAnalyzerButtonProps {
  disabled?: boolean;
  className?: string;
  onAnalyze: (url: string) => void;
}

export function GitHubAnalyzerButton({
  disabled = false,
  className,
  onAnalyze,
}: GitHubAnalyzerButtonProps) {
  const { t } = useTranslation("input");
  const [open, setOpen] = React.useState(false);
  const [url, setUrl] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);
  const inputRef = React.useRef<HTMLInputElement>(null);

  const canUse = !disabled;

  // 聚焦输入框
  React.useEffect(() => {
    if (open && inputRef.current) {
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }, [open]);

  // 解析 GitHub URL
  const parseRepoUrl = (rawUrl: string): { owner: string; repo: string } | null => {
    const trimmed = rawUrl.trim();
    // 支持格式: https://github.com/owner/repo, github.com/owner/repo, owner/repo
    const patterns = [
      /^https?:\/\/github\.com\/([^/]+)\/([^/\s?#]+)/,
      /^github\.com\/([^/]+)\/([^/\s?#]+)/,
      /^([^/\s]+)\/([^/\s?#]+)$/,
    ];

    for (const pattern of patterns) {
      const match = trimmed.match(pattern);
      if (match) {
        const owner = match[1]!;
        let repo = match[2]!;
        // 去掉 .git 后缀
        repo = repo.replace(/\.git$/, "");
        return { owner, repo };
      }
    }
    return null;
  };

  const handleSubmit = () => {
    if (!url.trim() || submitting) return;

    const parsed = parseRepoUrl(url);
    if (!parsed) {
      // 无效 URL，但仍然尝试分析
    }

    setSubmitting(true);
    onAnalyze(url.trim());
    setUrl("");
    setOpen(false);
    setSubmitting(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleSubmit();
    }
  };

  const parsed = parseRepoUrl(url);
  const isValidUrl = parsed !== null;

  return (
    <Popover open={open} onOpenChange={(v) => { if (canUse) setOpen(v); }}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={!canUse}
          className={cn(
            "h-8 rounded-full px-2 text-muted-foreground hover:text-foreground",
            open && "bg-primary/10 text-primary",
            className,
          )}
        >
          <Github className="size-4" />
        </Button>
      </PopoverTrigger>

      <PopoverContent align="end" side="top" className="w-[min(92vw,24rem)] gap-0 p-0">
        <PopoverHeader className="border-b px-4 py-3">
          <PopoverTitle className="flex items-center gap-2 text-sm">
            <Github className="size-4" />
            {t("github_analyzer.title", "GitHub 项目分析")}
          </PopoverTitle>
          <PopoverDescription className="text-[11px]">
            {t("github_analyzer.description", "输入 GitHub 仓库链接，AI 自动扫描代码安全、Bug 和依赖风险")}
          </PopoverDescription>
        </PopoverHeader>

        <div className="space-y-3 px-3 py-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              ref={inputRef}
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={t("github_analyzer.placeholder", "https://github.com/owner/repo")}
              className="h-9 pl-9 pr-3 text-sm"
              disabled={submitting}
            />
          </div>

          {url.trim() && isValidUrl && (
            <div className="flex items-center gap-2 rounded-md bg-muted/50 px-3 py-2 text-xs">
              <span className="text-muted-foreground">目标仓库：</span>
              <span className="font-mono font-medium text-foreground">
                {parsed.owner}/{parsed.repo}
              </span>
            </div>
          )}

          <Button
            onClick={handleSubmit}
            disabled={!url.trim() || submitting}
            className="w-full gap-2"
            size="sm"
          >
            {submitting ? (
              <LoaderCircle className="size-3.5 animate-spin" />
            ) : (
              <>
                <Search className="size-3.5" />
                {t("github_analyzer.scan", "开始分析")}
                <ArrowRight className="size-3.5" />
              </>
            )}
          </Button>

          <div className="text-center text-[10px] text-muted-foreground">
            {t("github_analyzer.hint", "分析将自动扫描安全漏洞、Bug 模式、依赖风险，并生成修复建议")}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
