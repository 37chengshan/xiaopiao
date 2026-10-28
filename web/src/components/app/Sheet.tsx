import type { KeyboardEventHandler, ReactNode } from "react";
import { Drawer } from "vaul";
import { cn } from "@/lib/utils";

/**
 * Apple-style bottom sheet (docs/DESIGN.md §4): surface, 20px top radius, grabber, scrim.
 * vaul tracks the finger 1:1 and projects release velocity, so dismissal follows the gesture.
 */
export function Sheet({
  open,
  onOpenChange,
  title,
  description,
  hideTitle = true,
  children,
  className,
  onKeyDown,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  title: string;
  description?: string;
  /** Visually hide the title (it still labels the dialog for assistive tech). */
  hideTitle?: boolean;
  children: ReactNode;
  className?: string;
  onKeyDown?: KeyboardEventHandler<HTMLDivElement>;
}) {
  return (
    <Drawer.Root open={open} onOpenChange={onOpenChange} shouldScaleBackground={false}>
      <Drawer.Portal>
        <Drawer.Overlay className="fixed inset-0 z-50 bg-scrim" />
        <Drawer.Content
          onKeyDown={onKeyDown}
          className={cn(
            "fixed inset-x-0 bottom-0 z-50 mx-auto flex max-h-[92dvh] max-w-[430px] flex-col rounded-t-[20px] bg-surface text-ink shadow-[0_-8px_40px_oklch(0_0_0/18%)] outline-none",
            className,
          )}
        >
          <div className="mx-auto mb-1 mt-2 h-[5px] w-9 shrink-0 rounded-full bg-ink-3/40" aria-hidden />
          <Drawer.Title className={hideTitle ? "sr-only" : "px-5 pb-1 pt-2 text-[20px] font-semibold leading-[26px] tracking-[-0.01em]"}>{title}</Drawer.Title>
          <Drawer.Description className={description ? "sr-only" : "hidden"}>{description}</Drawer.Description>
          {children}
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}
