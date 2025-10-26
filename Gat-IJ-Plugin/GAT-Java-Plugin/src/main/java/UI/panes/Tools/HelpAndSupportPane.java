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

public class HelpAndSupportPane extends JPanel {
    public static final String Name = "Help & Support";


    private static final class LinkItem {
        final String label, url;
        LinkItem(String label, String url){ this.label = label; this.url = url; }
    }

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

    private JComponent header() {
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Help & Support");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D()+3f));
        top.add(title, BorderLayout.CENTER);

        return top;
    }

    private static String buildHtml() {
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

    private static String escape(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private static void openInBrowser(String url){
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
