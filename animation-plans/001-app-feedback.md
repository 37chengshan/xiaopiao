# Android motion audit and execution scope

User-selected fixes: circle drawer opening/closing, system/gesture return, settings exit, ledger over-animation, receipt navigation, chat IME/focus. Audit used improve-animations; implementation follows animate and existing XpMotion/XpTiming.

Confirmed:
- CircleMenuDrawer mounts AnimatedVisibility(visible=true); first composition skips entry and parent removal prevents exit. Animate a retained layer, invoke close/navigation after completion.
- ChatComposer creates different Field nodes for empty/expanded layouts. Move one editor rather than destroying its IME connection. ChatScreen switches tab inset on an IME boolean; use max(IME inset, tab inset) through the whole transition.
- Ledger groups stagger on lazy composition, repeating during navigation and scrolling. Remove bulk entry; keep direct operation feedback.
- Selection actions are short Text in a 44dp capsule. Use a 48dp hit band with a 44dp visual box, matching icon actions.
- ReceiptPage inherits generic tab fade. Give this route its own restrained enter/return transition independent from printing.
- XpPushHost and page-specific BackHandlers compete. Retain outgoing content and unify system button / predictive gesture lifecycle; cancelled gestures must restore progress.
- ReceiptHaptics must guard createPredefined on API 29+; earlier devices use one-shot fallback. Simulator verification does not prove physical haptic feel.

Receipt sharing correction: tt explicitly limited this work to “发到圈子”. Do not alter image save, external share, presets or backgrounds. Share the exact printed receipt snapshot, preserve preview on back, require real account/circle choice and explicit publish confirmation; never substitute today's data or claim a local mock was published.
