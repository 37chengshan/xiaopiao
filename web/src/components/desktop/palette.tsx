import { Command } from "cmdk";
import { Languages, Plus, Printer, Search, Store } from "lucide-react";
import { getLocale, setLocale } from "@/lib/strings";
import { Modal } from "./dialogs";
import { PERSONAL_NAV, SETTINGS_ITEM, SHOP_NAV, go } from "./nav";
import { s } from "./strings";

/** cmdk palette (⌘K): jump between pages, log an entry, print today, switch halves or language. */
export function CommandPalette({
  open,
  onOpenChange,
  onRecord,
  onPrint,
  onLocale,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  onRecord: () => void;
  onPrint: () => void;
  onLocale: () => void;
}) {
  const run = (fn: () => void) => {
    onOpenChange(false);
    fn();
  };
  const shop = window.location.hash.startsWith("#/app/biz");
  return (
    <Modal open={open} onOpenChange={onOpenChange} label={s("cm.search")} width={520}>
      <Command loop className="bg-transparent text-d-ink">
        <div className="flex items-center gap-2 border-b border-d-hairline pb-3">
          <Search className="size-4 shrink-0 text-d-ink-3" strokeWidth={1.9} aria-hidden />
          <Command.Input
            autoFocus
            placeholder={s("cm.search")}
            className="h-6 w-full bg-transparent text-[14px] text-d-ink outline-none placeholder:text-d-ink-3"
          />
        </div>
        <Command.List className="max-h-[380px] overflow-y-auto pt-2">
          <Command.Empty className="py-8 text-center text-[13px] text-d-ink-3">{s("cm.empty")}</Command.Empty>

          <Command.Group heading={s("cm.actions")} className="[&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:pb-1 [&_[cmdk-group-heading]]:pt-1 [&_[cmdk-group-heading]]:text-[11px] [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:uppercase [&_[cmdk-group-heading]]:tracking-[0.06em] [&_[cmdk-group-heading]]:text-d-ink-3">
            <Item onSelect={() => run(onRecord)} icon={<Plus className="size-4 text-d-ink-3" strokeWidth={1.75} aria-hidden />}>
              {s("cm.record")}
            </Item>
            <Item onSelect={() => run(onPrint)} icon={<Printer className="size-4 text-d-ink-3" strokeWidth={1.75} aria-hidden />}>
              {s("cm.print")}
            </Item>
            <Item onSelect={() => run(onLocale)} icon={<Languages className="size-4 text-d-ink-3" strokeWidth={1.75} aria-hidden />}>
              {s("cm.language")}
            </Item>
            <Item onSelect={() => run(() => go(shop ? "/app" : "/app/biz"))} icon={<Store className="size-4 text-d-ink-3" strokeWidth={1.75} aria-hidden />}>
              {shop ? s("cm.gotoPersonal") : s("cm.gotoShop")}
            </Item>
          </Command.Group>

          <Command.Group heading={s("cm.pages")} className="[&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:pb-1 [&_[cmdk-group-heading]]:pt-3 [&_[cmdk-group-heading]]:text-[11px] [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:uppercase [&_[cmdk-group-heading]]:tracking-[0.06em] [&_[cmdk-group-heading]]:text-d-ink-3">
            {[...PERSONAL_NAV, SETTINGS_ITEM, ...SHOP_NAV].map((item) => {
              const Icon = item.icon;
              return (
                <Item key={item.to} onSelect={() => run(() => go(item.to))} icon={<Icon className="size-4" strokeWidth={1.75} aria-hidden />}>
                  {item.label()}
                </Item>
              );
            })}
          </Command.Group>
        </Command.List>
      </Command>
    </Modal>
  );
}

function Item({ children, onSelect, icon }: { children: React.ReactNode; onSelect: () => void; icon?: React.ReactNode }) {
  return (
    <Command.Item
      onSelect={onSelect}
      className="flex h-8 cursor-pointer select-none items-center gap-2 rounded-lg px-2 text-[13px] text-d-ink-2 data-[selected=true]:bg-d-fill data-[selected=true]:text-d-ink"
    >
      {icon}
      {children}
    </Command.Item>
  );
}
