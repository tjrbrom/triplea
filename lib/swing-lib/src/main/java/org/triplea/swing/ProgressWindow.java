package org.triplea.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/** A window used to display indeterminate progress during an operation. */
public class ProgressWindow extends JWindow {
  private static final long serialVersionUID = 4102671321734509406L;

  /**
   * Initializes a new instance of the {@code ProgressWindow} class.
   *
   * @param owner The frame from which the window is displayed; if {@code null} the shared owner
   *     will be used and this window will not be focusable.
   * @param title The progress message; may be {@code null}.
   */
  public ProgressWindow(final Frame owner, final String title) {
    super(owner);
    final JLabel label = new JLabel(title);
    label.setBorder(new EmptyBorder(10, 10, 10, 10));
    final JProgressBar progressBar = new JProgressBar();
    progressBar.setBorder(new EmptyBorder(10, 10, 10, 10));
    progressBar.setIndeterminate(true);
    final JPanel panel = new JPanel();
    panel.setBorder(new LineBorder(Color.BLACK));
    panel.setLayout(new BorderLayout());
    panel.add(BorderLayout.NORTH, label);
    panel.add(progressBar, BorderLayout.CENTER);
    setLayout(new BorderLayout());
    setSize(200, 80);
    add(panel, BorderLayout.CENTER);
    pack();
    setLocationRelativeTo(owner);
  }
}
