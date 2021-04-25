package org.triplea.swing;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;

/**
 * A JTabbedPane that whose tabs have a fixed width. Swing default is to use minimum sized tab for
 * each tab.
 */
public class JTabbedPaneWithFixedWidthTabs extends JTabbedPane {

  private static final long serialVersionUID = -625035680314209854L;
  private static final int DEFAULT_TAB_WIDTH = 130;
  private static final int DEFAULT_TAB_HEIGHT = 20;

  private int tabIndex = 0;
  private final Dimension tabDimension;

  public JTabbedPaneWithFixedWidthTabs() {
    this(DEFAULT_TAB_WIDTH, DEFAULT_TAB_HEIGHT);
  }

  private JTabbedPaneWithFixedWidthTabs(final int tabWidth, final int tabHeight) {
    tabDimension = new Dimension(tabWidth, tabHeight);
  }

  @Override
  public void addTab(final String tab, final Component contents) {
    super.addTab(tab, contents);
    final JLabel sizedLabel = new JLabel(tab);
    sizedLabel.setPreferredSize(tabDimension);

    super.setTabComponentAt(tabIndex, sizedLabel);
    tabIndex++;
  }
}
