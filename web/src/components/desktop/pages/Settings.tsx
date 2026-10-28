import { Download, Eraser, Info, Languages, LogIn, Palette, ShieldCheck } from "lucide-react";
import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { useLedger } from "@/services/LedgerProvider";
import { useTheme, type ThemePref } from "@/lib/theme";
import { useLocale } from "@/lib/strings";
import { downloadCsv } from "../csvExport";
import { Modal } from "../dialogs";
import { setLocalePref } from "../localePref";
import { Button, Card, Hint, Segmented } from "../ui";
import { s } from "../strings";
import pkg from "../../../../package.json";

function Row({ icon, label, hint, children }: { icon: ReactNode; label: string; hint?: string; children?: ReactNode }) {
  return (
    <div className="flex items-center gap-3 border-b border-d-hairline py-2.5 last:border-b-0">
      <span className="grid size-7 shrink-0 place-items-center rounded-lg bg-d-fill text-d-ink-2">{icon}</span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13px] text-d-ink">{label}</p>
        {hint && <p className="mt-0.5 text-[11.5px] leading-[16px] text-d-ink-3">{hint}</p>}
      </div>
      {children && <div className="shrink-0">{children}</div>}
    </div>
  );
}

export function SettingsPage() {
  const { txns, reset } = useLedger();
  const { pref, setPref } = useTheme();
  const locale = useLocale();
  const [confirming, setConfirming] = useState(false);

  const soon = () => toast(s("soon.title"), { description: s("soon.body") });

  return (
    <div className="mx-auto flex w-full max-w-[760px] flex-col gap-5">
      <Card title={s("st.account")}>
        <Row icon={<LogIn className="size-3.5" strokeWidth={1.8} aria-hidden />} label={s("st.accountValue")} hint={s("soon.body")}>
          <Button size="sm" variant="primary" onClick={soon}>
            {s("st.login")}
          </Button>
        </Row>
      </Card>

      <Card title={s("st.language")}>
        <Row icon={<Languages className="size-3.5" strokeWidth={1.8} aria-hidden />} label={s("st.interfaceLang")}>
          <Segmented
            value={locale}
            onChange={(v) => setLocalePref(v)}
            label={s("st.language")}
            size="sm"
            options={[
              { value: "zh", label: s("st.zh") },
              { value: "en", label: s("st.en") },
            ]}
          />
        </Row>
      </Card>

      <Card title={s("st.appearance")}>
        <Row icon={<Palette className="size-3.5" strokeWidth={1.8} aria-hidden />} label={s("st.theme")}>
          <Segmented
            value={pref}
            onChange={(v: ThemePref) => setPref(v)}
            label={s("st.appearance")}
            size="sm"
            options={[
              { value: "system", label: s("st.system") },
              { value: "light", label: s("st.light") },
              { value: "dark", label: s("st.dark") },
            ]}
          />
        </Row>

      </Card>

      <Card title={s("st.data")}>
        <Row icon={<Download className="size-3.5" strokeWidth={1.8} aria-hidden />} label={s("st.exportData")} hint={`${txns.length} ${s("ledger.itemUnit")}`}>
          <Button
            size="sm"
            icon={Download}
            aria-label={s("st.exportData")}
            onClick={() => {
              downloadCsv("xiaopiao-all.csv", txns);
              toast(s("st.exported"));
            }}
          />
        </Row>
        <Row icon={<Eraser className="size-3.5" strokeWidth={1.8} aria-hidden />} label={s("st.clear")} hint={s("st.clearBody")}>
          <Button size="sm" variant="ghost" className="text-d-negative hover:bg-d-negative/10" onClick={() => setConfirming(true)}>
            {s("common.delete")}
          </Button>
        </Row>
      </Card>

      <Card title={s("st.storage")}>
        <Row icon={<Info className="size-3.5" strokeWidth={1.8} aria-hidden />} label={s("st.storageValue")} hint={s("st.storageBody")} />
      </Card>

      <Card title={s("st.privacyTitle")} action={<ShieldCheck className="size-3.5 text-d-ink-3" strokeWidth={1.8} aria-hidden />}>
        <p className="text-[12.5px] leading-[21px] text-d-ink-2">{s("st.privacyBody")}</p>
      </Card>

      <Card title={s("st.about")}>
        <Row icon={<Info className="size-3.5" strokeWidth={1.8} aria-hidden />} label={s("st.version")}>
          <span className="num text-[12px] text-d-ink-2">{pkg.version} · Web</span>
        </Row>
        <div className="pt-3">
          <Hint>{s("brand")} · 认真生活，留下凭证。</Hint>
        </div>
      </Card>

      <Modal open={confirming} onOpenChange={setConfirming} label={s("st.clearTitle")} width={420}>
        <h2 className="text-[15px] font-semibold text-d-ink">{s("st.clearTitle")}</h2>
        <p className="mt-1.5 text-[12.5px] leading-[20px] text-d-ink-2">{s("st.clearBody")}</p>
        <div className="mt-4 flex justify-end gap-2">
          <Button onClick={() => setConfirming(false)}>{s("common.cancel")}</Button>
          <Button
            variant="primary"
            onClick={() => {
              void reset().then(() => {
                setConfirming(false);
                toast(s("st.cleared"));
              });
            }}
          >
            {s("common.confirm")}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
