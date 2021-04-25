package tools.map.making.ui.xml;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.experimental.UtilityClass;
import org.triplea.swing.JButtonBuilder;
import tools.map.making.ConnectionFinder;

@UtilityClass
public class XmlUtilitiesPanel {

  public JPanel build() {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
    panel.add(Box.createVerticalStrut(30));
    panel.add(new JLabel("Game XML Utilities:"));
    panel.add(
        new JLabel(
            "Sorry but for now the only XML creator is Wisconsin's 'Part 2' of his map maker."));
    panel.add(Box.createVerticalStrut(30));
    panel.add(
        new JButtonBuilder("Run the Connection Finder")
            .actionListener(ConnectionFinder::run)
            .build());
    panel.add(Box.createVerticalStrut(30));
    return panel;
  }
}
