import { createFileRoute } from "@tanstack/react-router";
import type { LucideIcon } from "lucide-react";
import {
  Cloud,
  ImagePlus,
  Lightbulb,
  Mic,
  Palette,
  Paperclip,
  Puzzle,
  WandSparkles,
} from "lucide-react";
import { useMemo, useState } from "react";

export const Route = createFileRoute("/")({
  component: Index,
});

// IMPORTANT: Replace this entire placeholder with the user's real home page.
// For multi-page sites (About / Services / Contact, etc.), add separate files
// under src/routes/ (about.tsx / services.tsx / contact.tsx). Do not pack
// every page into this file.

const PLACEHOLDER_MASCOT_SRC =
  "https://mdn.alipayobjects.com/huamei_ne5osn/afts/img/A*EbGBRKnZqOwAAAAAgBAAAAgAekqLAQ/original";

const FEATURES: ReadonlyArray<{ icon: LucideIcon; text: string }> = [
  { icon: Cloud, text: "懂你所想，自动接入所需云服务" },
  { icon: ImagePlus, text: "上传图片，智能识别内容与用途" },
  { icon: Paperclip, text: "上传文档附件，支持多模态内容生成" },
  { icon: WandSparkles, text: "哪里想改，点哪里就能改" },
  { icon: Mic, text: "开口即调整，边说边完成" },
  { icon: Palette, text: "一键切换风格，让作品更具表现力" },
  { icon: Puzzle, text: "接入专业技能，让生成效果更进一步" },
];

const FEATURE_ITEM_HEIGHT = 34;
const VISIBLE_FEATURE_COUNT = 3;

function FeatureCarousel() {
  const [paused, setPaused] = useState(false);
  const loopFeatures = useMemo(
    () =>
      Array.from({ length: 3 }, (_, copy) =>
        FEATURES.map((feature, index) => ({
          ...feature,
          key: `${feature.text}-${copy}-${index}`,
        })),
      ).flat(),
    [],
  );

  return (
    <div
      className="relative w-max overflow-hidden"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
    >
      <div
        className="pointer-events-none absolute inset-x-0 top-0 z-10 h-5"
        style={{
          background:
            "linear-gradient(to bottom, #fff 0%, rgba(255, 255, 255, 0) 100%)",
        }}
      />
      <div
        className="overflow-hidden"
        style={{ height: VISIBLE_FEATURE_COUNT * FEATURE_ITEM_HEIGHT }}
      >
        <div
          style={{
            animation: "qmuse-blank-scroll 10s linear infinite",
            animationDelay: "0.5s",
            animationPlayState: paused ? "paused" : "running",
            display: "flex",
            flexDirection: "column",
          }}
        >
          {loopFeatures.map((feature) => {
            const Icon = feature.icon;
            return (
              <div
                key={feature.key}
                className="mb-4 flex h-[18px] items-center"
              >
                <Icon
                  className="size-4 shrink-0 text-[#979797]"
                  strokeWidth={1.75}
                />
                <span className="ml-2 whitespace-nowrap text-sm leading-[18px] text-[#979797]">
                  {feature.text}
                </span>
              </div>
            );
          })}
        </div>
      </div>
      <div
        className="pointer-events-none absolute inset-x-0 bottom-0 z-10 h-5"
        style={{
          background:
            "linear-gradient(to top, #fff 0%, rgba(255, 255, 255, 0) 100%)",
        }}
      />
    </div>
  );
}

function Index() {
  return (
    <div
      className="grid min-h-screen place-items-center bg-white"
      data-blank-page-placeholder="REMOVE_THIS"
    >
      <style>{`
        @keyframes qmuse-blank-scroll {
          from { transform: translateY(0); }
          to { transform: translateY(-${FEATURES.length * FEATURE_ITEM_HEIGHT}px); }
        }
      `}</style>
      <div className="flex flex-col items-center">
        <img
          src={PLACEHOLDER_MASCOT_SRC}
          alt=""
          width={190}
          height={190}
          className="mb-4 size-[190px] object-contain"
        />
        <h1 className="mb-7 flex items-center gap-1.5 text-base font-semibold leading-[26px] text-[#040927]">
          <Lightbulb className="size-4 shrink-0" strokeWidth={2} />
          说出你的创作需求，让灵感触手可及，让需求极速上线 ~
        </h1>
        <FeatureCarousel />
      </div>
    </div>
  );
}
