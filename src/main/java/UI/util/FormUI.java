package UI.util;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Small Swing UI helpers to keep layout and styling consistent across panes.
 *
 * <p>
 * Responsibilities:
 * </p>
 * <ul>
 *   <li>Standard "section box" panels with titled borders and optional info badges.</li>
 *   <li>Common layout primitives (rows, columns, label/value grids).</li>
 *   <li>Utility helpers for width normalization and control sizing.</li>
 *   <li>Reusable "info" badge (ⓘ) icons with wrapped tooltips.</li>
 * </ul>
 *
 * <p>
 * All methods are static; the class is not instantiable.
 * </p>
 */
public final class FormUI {
    private FormUI() {}


    /**
     * Create a standard titled section box.
     *
     * <p>
     * Produces a {@link JPanel} with:
     * </p>
     * <ul>
     *   <li>An etched {@link javax.swing.border.TitledBorder} using {@code title}.</li>
     *   <li>{@code content} placed in the center.</li>
     *   <li>Width normalized via {@link #normalizeSectionWidth(JComponent)} so that
     *       multiple sections line up nicely in a vertical {@link BoxLayout}.</li>
     * </ul>
     *
     * @param title
     *        Title text for the border.
     *
     * @param content
     *        Inner component for this section.
     *
     * @return A {@link JPanel} ready to insert into a BoxLayout column.
     */
    public static JPanel box(String title, Component content) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));
        outer.add(content, BorderLayout.CENTER);

        // Keep consistent width across all sections
        normalizeSectionWidth(outer);
        return outer;
    }

    /**
     * Create a titled section box with a small "info" badge docked at top-right.
     *
     * <p>
     * The badge is a clickable-looking {@link JLabel} with an info icon and a tooltip
     * built from {@code helpHtml}. It's placed in the same visual row as {@code content}
     * (no extra header row), using a nested BorderLayout + GridBagLayout trick.
     * </p>
     *
     * <p>
     * The outer panel is also given compact inner padding, and normalized width.
     * </p>
     *
     * @param title
     *        Border title for the section.
     *
     * @param content
     *        The main content component.
     *
     * @param helpHtml
     *        Short HTML snippet explaining the setting/section.
     *        This will be wrapped in a nicer tooltip via {@link #wrapTooltip(String, int)}.
     *
     * @return A {@link JPanel} containing the content and a help badge in the corner.
     */
    public static JPanel boxWithHelp(String title, JComponent content, String helpHtml) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));

        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(false);
        inner.add(content, BorderLayout.CENTER);

        // Badge docked to the top-right
        JLabel info = createInfoBadge(helpHtml);
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0;
        gc.anchor = GridBagConstraints.NORTH;   // pin to top
        gc.insets = new Insets(2, 6, 0, 0);     // slight nudge down; small left gap
        east.add(info, gc);

        inner.add(east, BorderLayout.EAST);
        outer.add(inner, BorderLayout.CENTER);

        // Compact padding inside the titled border
        outer.setBorder(BorderFactory.createCompoundBorder(
                outer.getBorder(),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)
        ));

        normalizeSectionWidth(outer);
        return outer;
    }

    /**
     * Force a section box (or any component) to expand horizontally
     * but keep its preferred height in layouts like BoxLayout(Y_AXIS).
     *
     * <p>
     * We do this by:
     * </p>
     * <ul>
     *   <li>Left-aligning the component.</li>
     *   <li>Setting {@code maximumSize} to (infinite width, preferred height),
     *       which prevents unwanted vertical stretching while allowing
     *       horizontal fill.</li>
     * </ul>
     *
     * @param c
     *        The panel or component to normalize.
     */
    public static void normalizeSectionWidth(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension pref = c.getPreferredSize();
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    /**
     * Convenience for a single horizontal row of components with small gaps,
     * left-aligned.
     *
     * <p>
     * Good for simple "Label + Field + Button" lines.
     * </p>
     *
     * @param comps
     *        Components to add in order.
     *
     * @return A {@link JPanel} using {@link FlowLayout} (LEFT, hgap=8, vgap=4).
     */
    public static JPanel row(JComponent... comps) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent c : comps) r.add(c);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    /**
     * Convenience for a vertical stack of components with a small vertical spacer
     * (6 px) between each.
     *
     * <p>
     * Each child is left-aligned so they line up nicely in pane layouts.
     * </p>
     *
     * @param comps
     *        Components to stack in order.
     *
     * @return A {@link JPanel} using {@link BoxLayout} on the Y axis.
     */
    public static JPanel column(JComponent... comps) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        for (JComponent c : comps) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(c);
            col.add(Box.createVerticalStrut(6));
        }
        return col;
    }

    /**
     * Build a flexible 2-column grid for label/value pairs.
     *
     * <p>
     * The left column is anchored WEST and does not grow.
     * The right column ({@code rc}) expands horizontally (weightx=1, fill=HORIZONTAL),
     * making it ideal for text fields or anything that should stretch.
     * </p>
     *
     * <p>
     * You pass components in alternating key/value order:
     * {@code grid2(new JLabel("Name"), nameField, new JLabel("Size"), sizeSpinner, ...)}.
     * </p>
     *
     * @param kvPairs
     *        Even-length list of components, in label,value,label,value... order.
     *
     * @return A left-aligned {@link JPanel} using {@link GridBagLayout}.
     */
    public static JPanel grid2(Component... kvPairs) {
        JPanel g = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        GridBagConstraints rc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = 0; lc.anchor = GridBagConstraints.WEST; lc.insets = new Insets(3,3,3,3);
        rc.gridx = 1; rc.gridy = 0; rc.weightx = 1; rc.fill = GridBagConstraints.HORIZONTAL; rc.insets = new Insets(3,3,3,3);
        for (int i = 0; i < kvPairs.length; i += 2) {
            g.add(kvPairs[i], lc);
            g.add(kvPairs[i+1], rc);
            lc.gridy++; rc.gridy++;
        }
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
    }

    /**
     * Similar to {@link #grid2(Component...)}, but the right column
     * does NOT stretch horizontally.
     *
     * <p>
     * This is useful for spinners, short combos, etc. where you want label and control
     * snug together without the control expanding.
     * </p>
     *
     * @param kvPairs
     *        Even-length list of components, label then value.
     *
     * @return A left-aligned {@link JPanel} using {@link GridBagLayout}
     *         with {@code fill=NONE} and {@code weightx=0} on the right column.
     */
    public static JPanel grid2Compact(Component... kvPairs) {
        JPanel g = new JPanel(new GridBagLayout());
        GridBagConstraints l = new GridBagConstraints();
        GridBagConstraints r = new GridBagConstraints();
        l.gridx = 0; l.gridy = 0; l.anchor = GridBagConstraints.WEST; l.insets = new Insets(3,3,3,6);
        r.gridx = 1; r.gridy = 0; r.anchor = GridBagConstraints.WEST; r.insets = new Insets(3,0,3,3);
        r.weightx = 0; r.fill = GridBagConstraints.NONE;  // <- no stretch
        for (int i = 0; i < kvPairs.length; i += 2) {
            g.add(kvPairs[i], l); g.add(kvPairs[i+1], r);
            l.gridy++; r.gridy++;
        }
        g.setAlignmentX(Component.LEFT_ALIGNMENT);
        return g;
    }

    /**
     * Wrap a component in a tiny, left-aligned container so that
     * BoxLayout won't center it or stretch it oddly.
     *
     * <p>
     * This is handy for single checkboxes or buttons you want visually
     * pinned to the left edge in a vertical column.
     * </p>
     *
     * @param c
     *        The component to wrap.
     *
     * @return A transparent {@link JPanel} with {@link FlowLayout}(LEFT,0,0)
     *         containing {@code c}, also left-aligned.
     */
    public static JComponent leftWrap(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /**
     * Constrain a component to a fixed max width and return a wrapper panel
     * suitable for use in BoxLayout.
     *
     * <p>
     * We clamp the component's preferred / min / max size to the given width,
     * then return a small left-aligned wrapper so layouts respect it.
     * Common use: make a spinner or combo box not sprawl across the entire row.
     * </p>
     *
     * @param c
     *        The control to clamp (e.g. a {@link JSpinner}).
     *
     * @param width
     *        Maximum width in pixels.
     *
     * @return A transparent wrapper panel containing {@code c}.
     */
    public static JComponent limitWidth(JComponent c, int width) {
        Dimension d = c.getPreferredSize();
        d = new Dimension(Math.min(d.width, width), d.height);
        c.setPreferredSize(d); c.setMinimumSize(d); c.setMaximumSize(d);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    /**
     * Create a small "info" badge label (ⓘ-style icon) with a wrapped tooltip.
     *
     * <p>
     * Behavior:
     * </p>
     * <ul>
     *   <li>Scales the standard LAF information icon (or uses a fallback vector icon).</li>
     *   <li>Sets an HTML tooltip produced by {@link #wrapTooltip(String, int)} so text wraps.</li>
     *   <li>Sets pointer cursor to hint interactivity, and marks accessible name "More info".</li>
     * </ul>
     *
     * @param helpHtml
     *        Short HTML snippet explaining the associated setting/section.
     *        This is embedded into the tooltip body.
     *
     * @return A {@link JLabel} containing only the icon, no text.
     */
    public static JLabel createInfoBadge(String helpHtml) {
        JLabel b = new JLabel(getInfoIcon(14)); // 14px icon
        b.setText(null);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setToolTipText(wrapTooltip(helpHtml, 360)); // wrapped tooltip
        b.getAccessibleContext().setAccessibleName("More info");
        return b;
    }

    /**
     * Wrap arbitrary HTML content with a fixed-width body so Swing tooltips
     * line-break nicely instead of running in one long line.
     *
     * <p>
     * This is used by {@link #createInfoBadge(String)} to generate tooltips that
     * are actually readable.
     * </p>
     *
     * @param innerHtml
     *        Raw HTML snippet (no outer &lt;html&gt; required).
     *
     * @param widthPx
     *        Max width in pixels for the tooltip body.
     *
     * @return A full {@code <html><body style='width:...'>...</body></html>} string.
     */
    public static String wrapTooltip(String innerHtml, int widthPx) {
        return "<html><body style='width:" + widthPx + "px; padding:6px;'>" + innerHtml + "</body></html>";
    }

    /**
     * Return a scaled "info" icon of the requested pixel size.
     *
     * <p>
     * We try in order:
     * </p>
     * <ol>
     *   <li>UIManager's {@code "OptionPane.informationIcon"} as an {@link ImageIcon},
     *       scaled smoothly to {@code sizePx}.</li>
     *   <li>If the LAF icon isn't an {@link ImageIcon} (but is still an {@link Icon}),
     *       we paint it into a {@link BufferedImage} then scale.</li>
     *   <li>If all else fails, we return a fallback vector icon
     *       {@link MiniInfoIcon} that draws a small ⓘ.</li>
     * </ol>
     *
     * @param sizePx
     *        Desired icon size (width = height = {@code sizePx}).
     *
     * @return A non-null {@link Icon} approximately {@code sizePx}×{@code sizePx}.
     */
    public static Icon getInfoIcon(int sizePx) {
        Icon ui = UIManager.getIcon("OptionPane.informationIcon");
        if (ui instanceof ImageIcon) {
            Image img = ((ImageIcon) ui).getImage();
            Image scaled = img.getScaledInstance(sizePx, sizePx, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } else if (ui != null) {
            BufferedImage bi = new BufferedImage(ui.getIconWidth(), ui.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ui.paintIcon(null, g2, 0, 0);
            g2.dispose();
            Image scaled = bi.getScaledInstance(sizePx, sizePx, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }
        return new MiniInfoIcon(sizePx); // fallback vector
    }

    /**
     * Small vector-drawn fallback "ⓘ" icon.
     *
     * <p>
     * Drawn dynamically if no suitable LAF icon is available.
     * Paints a translucent circle outline plus a vertical "i" stem and dot.
     * </p>
     */
    static final class MiniInfoIcon implements Icon {
        private final int sz;
        /**
         * Create a new fallback info icon with the given size.
         *
         * @param size
         *        Icon width/height in pixels.
         */
        MiniInfoIcon(int size) { this.sz = size; }
        /**
         * @return The width of this icon in pixels.
         */
        public int getIconWidth()  { return sz; }
        /**
         * @return The height of this icon in pixels.
         */
        public int getIconHeight() { return sz; }
        /**
         * Paint the "ⓘ" badge.
         *
         * <p>
         * We:
         * </p>
         * <ul>
         *   <li>Enable antialiasing for smoother circles/lines.</li>
         *   <li>Draw a soft, semi-transparent circle outline.</li>
         *   <li>Add the "i" stem and dot in the center.</li>
         * </ul>
         *
         * @param c
         *        The component asking for the icon (not used heavily, but may inform LAF colors).
         *
         * @param g
         *        The graphics context to paint into.
         *
         * @param x
         *        X offset where the icon should start.
         *
         * @param y
         *        Y offset where the icon should start.
         */
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.foreground");
            if (fg == null) fg = new Color(190, 200, 210);
            int d = sz - 1;
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 180));
            g2.drawOval(x, y, d, d);                    // circle
            int cx = x + sz / 2;
            g2.drawLine(cx, y + (int)(sz * 0.38),       // stem
                    cx, y + (int)(sz * 0.78));
            g2.fillOval(cx - 1, y + (int)(sz * 0.25), 2, 2); // dot
            g2.dispose();
        }
    }
}
