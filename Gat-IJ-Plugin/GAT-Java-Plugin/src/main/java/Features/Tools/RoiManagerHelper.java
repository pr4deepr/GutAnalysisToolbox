package Features.Tools;

import ij.plugin.frame.RoiManager;

/**
 * Helpers for working with ImageJ's global RoiManager in a safe/reusable way.
 *
 * Why this exists:
 *  - The RoiManager in ImageJ is effectively a singleton, but plugins can
 *    replace it behind our back.
 *  - We sometimes want to auto-create one (headless) and later clean it up,
 *    but we *don't* want to close a RoiManager the user already had open.
 *
 * This class wraps those patterns:
 *  - ensureGlobalRM(): get or create an RoiManager, hidden.
 *  - syncToSingleton(): refresh our reference if a plugin replaced it.
 *  - maybeCloseRM(): only dispose it if *we* were the ones who created it.
 */
public final class RoiManagerHelper {
    private RoiManagerHelper() {}

    /**
     * Small handle object returned by ensureGlobalRM().
     * It tells us:
     *  - rm        : the RoiManager instance to use going forward
     *  - weOpened  : true if we created it now, false if we reused an existing one
     *
     * Call maybeCloseRM(handle) at the end of a pipeline to politely close it
     * only if weOpened == true.
     */
    public static final class RmHandle {
        public final RoiManager rm;
        public final boolean weOpened;
        public RmHandle(RoiManager rm, boolean weOpened) { this.rm = rm; this.weOpened = weOpened; }
    }

    /**
     * Ensure there is a global RoiManager available and return it.
     *
     * Behavior:
     *  - If one already exists (RoiManager.getInstance2()), reuse it.
     *  - Otherwise, create a new RoiManager(), which becomes the singleton.
     *  - Always hide the window initially (we control visibility manually).
     *
     * @return RmHandle containing the RoiManager plus a flag telling us if we created it.
     */
    public static RmHandle ensureGlobalRM() {
        RoiManager rm = RoiManager.getInstance2();
        boolean weOpened = false;
        if (rm == null) { rm = new RoiManager(); weOpened = true; } // becomes the singleton
        rm.setVisible(false); // keep hidden; let the UI show it as needed
        return new RmHandle(rm, weOpened);
    }

    /**
     * RoiManager is a weird global singleton that some plugins will destroy/recreate.
     * If that happens, your stored reference can go stale.
     *
     * syncToSingleton() is a tiny helper to resync a caller's local reference:
     *
     *     RmHandle h = ensureGlobalRM();
     *     RoiManager rm = h.rm;
     *     ...
     *     syncToSingleton(new RoiManager[]{ rm });
     *     // now rm points at RoiManager.getInstance2() again, in case it changed
     *
     * We take a single-element array because Java can't pass refs by ref for locals.
     *
     * @param ref single-element array containing your RoiManager reference to refresh.
     */
    public static void syncToSingleton(RoiManager[] ref) {
        RoiManager s = RoiManager.getInstance2();
        if (s != null) ref[0] = s;
    }

    /**
     * Cleanly shut down the RoiManager if (and only if) we were the ones who created it.
     *
     * Call this at the end of a long pipeline to avoid leaving stray RoiManager
     * windows around. If the user had an RoiManager open before we started, we
     * don't close theirs.
     *
     * @param h handle returned from ensureGlobalRM() at the start.
     */
    public static void maybeCloseRM(RmHandle h) {
        if (h != null && h.weOpened && h.rm != null) {
            try { h.rm.reset(); } catch (Throwable ignore) {}
            try { h.rm.setVisible(false); } catch (Throwable ignore) {}
            try { h.rm.close(); } catch (Throwable ignore) {}   // IJ API close
            try { h.rm.dispose(); } catch (Throwable ignore) {} // AWT Frame disposal
        }
    }
}
