package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
/**
 * {@code HelpAndSupportPane} is a Swing {@link JPanel} that displays a
 * Help &amp; Support section within the UI.
 *
 * <p>This panel provides:
 * <ul>
 *   <li>A header ("Help & Support").</li>
 *   <li>A scrollable HTML view listing useful support/resources links
 *       such as documentation, tutorials, issue reporting, etc.</li>
 *   <li>Clickable hyperlinks that open in the user's default browser.</li>
 * </ul>
 *
 * <p>The panel is intended as a read-only reference for users who need
 * additional guidance, want to report issues, or want to access external
 * resources related to the application.
 *
 * <p>Visual notes:
 * <ul>
 *   <li>The body text is rendered in white on (presumably) a dark background.</li>
 *   <li>Links use standard anchor tags in the embedded HTML.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *     Navigator nav = ...;
 *     JPanel helpPane = new HelpAndSupportPane(nav);
 *     someParentContainer.add(helpPane);
 * </pre>
 *
 * The {@link Navigator} argument is accepted for potential future integration
 * with the rest of the UI, but is not currently used internally.
 */
public class HelpAndSupportPane extends JPanel {
    public static final String Name = "Help & Support";


    /**
     * Immutable value object representing a support link entry.
     *
     * <p>Each {@code LinkItem} consists of:
     * <ul>
     *   <li>A human-readable label suitable for display in the UI.</li>
     *   <li>A URL target that will be opened in the user's browser.</li>
     * </ul>
     *
     * This is used by {@link #links()} and ultimately rendered in the HTML
     * via {@link #buildHtml()}.
     */
    static final class LinkItem {
        final String label, url;
        /**
         * Constructs a new {@code LinkItem}.
         *
         * @param label human-readable label describing this link
         * @param url   absolute URL associated with this link
         */
        LinkItem(String label, String url){ this.label = label; this.url = url; }
    }

    /**
     * Builds and returns the list of external help/support resources.
     *
     * <p>This includes documentation, tutorials, release notes, sample
     * datasets, issue reporting, and publication links.
     *
     * <p>The list order is the order they will appear in the rendered HTML.
     *
     * @return a {@link List} of {@link LinkItem} objects representing each
     *         support resource link
     */
    private static List<LinkItem> links(){
        List<LinkItem> L = new ArrayList<>();
        L.add(new LinkItem("Step-by-step tutorial (Documentation)", "https://gut-analysis-toolbox.gitbook.io/docs/"));
        L.add(new LinkItem("Video tutorials (YouTube playlist)", "https://www.youtube.com/channel/UC03y9hDwDsVAhgeebyWpoew/playlists"));
        L.add(new LinkItem("Latest updates (Release notes)", "https://github.com/pr4deepr/GutAnalysisToolbox/releases/"));
        L.add(new LinkItem("Download sample data (Zenodo)", "https://zenodo.org/doi/10.5281/zenodo.10590347"));

        L.add(new LinkItem("Report issues (Google Form)", "https://forms.gle/6AampkLzhVJc5ygx9"));
        L.add(new LinkItem("Report issues (GitHub)", "https://github.com/pr4deepr/GutAnalysisToolbox/issues"));
        L.add(new LinkItem("Ask on image.sc forum", "https://forum.image.sc/"));
        L.add(new LinkItem("Publication", "https://journals.biologists.com/jcs/article/137/20/jcs261950/362542/Gut-Analysis-Toolbox-automating-quantitative"));
        return L;
    }

    /**
     * Constructs the Help &amp; Support panel.
     *
     * <p>This sets up:
     * <ul>
     *   <li>A header component created by {@link #header()} in the north region.</li>
     *   <li>A scrollable {@link JEditorPane} in the center region that shows HTML built by {@link #buildHtml()}.</li>
     * </ul>
     *
     * <p>The HTML area is non-editable and intercepts hyperlink clicks.
     * When a link is activated, {@link #openInBrowser(String)} is called
     * to launch the user's default browser.
     *
     * @param navigator a {@link Navigator} instance from the surrounding
     *                  UI framework. It is not currently used, but is accepted
     *                  for future extensibility (e.g. internal navigation,
     *                  analytics, context switching).
     */
    public HelpAndSupportPane(Navigator navigator){
        super(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JEditorPane html = new JEditorPane("text/html", buildHtml());
        html.setEditable(false);
        html.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        html.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openInBrowser(e.getURL().toString());
            }
        });

        JScrollPane scroll = new JScrollPane(html,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);


        add(header(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Builds and returns the header component (top bar) for this pane.
     *
     * <p>The header currently consists of a bold "Help & Support" label,
     * styled slightly larger than default.
     *
     * <p>Returned component is safe to add directly into a BorderLayout.NORTH.
     *
     * @return a {@link JComponent} representing the header section
     */
    private JComponent header() {
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Help & Support");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D()+3f));
        top.add(title, BorderLayout.CENTER);

        return top;
    }

    /**
     * Builds the HTML string rendered inside the {@link JEditorPane}.
     *
     * <p>This method:
     * <ol>
     *   <li>Iterates through all {@link LinkItem}s from {@link #links()}.</li>
     *   <li>Escapes each link label via {@link #escape(String)} to avoid HTML injection.</li>
     *   <li>Outputs an unordered list (&lt;ul&gt;) of entries, each showing:
     *       &lt;b&gt;label&lt;/b&gt; — &lt;a href="url"&gt;url&lt;/a&gt;.</li>
     *   <li>Wraps that list into a styled HTML template with inline CSS.</li>
     * </ol>
     *
     * <p>The CSS sets a sans-serif font, white text, and disables underline
     * on idle links.
     *
     * @return a complete HTML document as a {@link String} suitable for
     *         {@code new JEditorPane("text/html", ...)}
     */
    static String buildHtml() {
        StringBuilder ul = new StringBuilder();
        for (LinkItem li : links()) {
            ul.append("<li><b>")
                    .append(escape(li.label))
                    .append("</b> — <a href='")
                    .append(li.url)
                    .append("'>")
                    .append(li.url)
                    .append("</a></li>");
        }

        return "<html><head><style>"
                + "body{font-family:Segoe UI,Roboto,Arial,sans-serif;color:#fff;font-size:13px;margin:0;padding:0}"
                + "h1{font-size:18px;margin:0 0 8px 0}"
                + ".wrap{padding:4px 8px}"
                + ".section{margin:10px 0 0 0}"
                + "ul{margin:6px 0 0 18px}"
                + "a{text-decoration:none}"
                + "a:hover{text-decoration:underline}"
                + "</style></head><body>"
                + "<div class='wrap'>"
                + "<div class='section'><b>Click a link for more information:</b>"
                + "<ul>" + ul + "</ul>"
                + "</div></div></body></html>";
    }

    /**
     * Escapes a plain text string for safe embedding inside HTML.
     *
     * <p>This method replaces the special characters {@code &}, {@code <}, and {@code >}
     * with their corresponding entities {@code &amp;}, {@code &lt;}, and {@code &gt;}
     * to prevent breaking markup or accidental HTML injection.
     *
     * @param s the raw string to escape
     * @return the escaped string, safe for concatenation into HTML text nodes
     */
    private static String escape(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    /**
     * Attempts to open the given URL in the user's default browser.
     *
     * <p>If desktop browsing is unsupported on the current platform or
     * if an exception occurs (for example, malformed URL / security restriction),
     * this method will display a {@link JOptionPane} warning or error dialog.
     *
     * <p>This method uses {@link java.awt.Desktop} where available.
     *
     * @param url the URL to open; should be an absolute URI (e.g. "https://...")
     */
    static void openInBrowser(String url){
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                JOptionPane.showMessageDialog(null, "Desktop browsing not supported on this system.", "Open link", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Failed to open:\n" + url + "\n\n" + ex, "Open link", JOptionPane.ERROR_MESSAGE);
        }
    }


}
