package games.strategy.triplea.printgenerator;

import games.strategy.engine.data.GameData;
import java.awt.BorderLayout;
import java.io.File;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.jpanel.JPanelBuilder;

/** The top-level UI component for configuring the Setup Chart exporter. */
public class SetupFrame extends JPanel {
  private static final long serialVersionUID = 7308943603423170303L;
  private final JTextField outField;
  private final JFileChooser outChooser;
  private final JRadioButton originalState;
  private final GameData data;
  private File outDir;

  public SetupFrame(final GameData data) {
    super(new BorderLayout());
    this.data = data;
    final JButton outDirButton = new JButton();
    final JButton runButton = new JButton();
    outField = new JTextField(15);
    outChooser = new JFileChooser();
    outChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    final JRadioButton currentState = new JRadioButton();
    originalState = new JRadioButton();
    final ButtonGroup radioButtonGroup = new ButtonGroup();

    currentState.setText("Current Position/State");
    originalState.setText("Starting Position/State");
    radioButtonGroup.add(currentState);
    radioButtonGroup.add(originalState);
    originalState.setSelected(true);
    outDirButton.setText("Choose the Output Directory");
    outDirButton.addActionListener(
        e -> {
          final int returnVal = outChooser.showOpenDialog(null);
          if (returnVal == JFileChooser.APPROVE_OPTION) {
            final File outDir = outChooser.getSelectedFile();
            outField.setText(outDir.getAbsolutePath());
          }
        });
    runButton.setText("Generate the Files");
    runButton.addActionListener(
        e -> {
          if (!outField.getText().isEmpty()) {
            outDir = new File(outField.getText());
            final PrintGenerationData printData =
                PrintGenerationData.builder().outDir(outDir).data(this.data).build();
            new InitialSetup().run(printData, originalState.isSelected());
            JOptionPane.showMessageDialog(null, "Done!", "Done!", JOptionPane.INFORMATION_MESSAGE);
          } else {
            JOptionPane.showMessageDialog(
                null,
                "You need to select an Output Directory.",
                "Select an Output Directory!",
                JOptionPane.ERROR_MESSAGE);
          }
        });

    final JPanel infoPanel =
        new JPanelBuilder()
            .gridLayout(3, 1)
            .add(
                JLabelBuilder.builder()
                    .text("This utility will export the map's either current or ")
                    .build())
            .add(
                JLabelBuilder.builder()
                    .text("beginning state exactly like the board game, so you ")
                    .build())
            .add(
                JLabelBuilder.builder()
                    .text("will get Setup Charts, Unit Information, etc.")
                    .build())
            .build();

    super.add(infoPanel, BorderLayout.NORTH);

    final JPanel textButtonRadioPanel =
        new JPanelBuilder()
            .borderLayout()
            .addWest(outField)
            .addEast(outDirButton)
            .addSouth(
                new JPanelBuilder().gridLayout(1, 2).add(originalState).add(currentState).build())
            .build();

    super.add(textButtonRadioPanel, BorderLayout.CENTER);

    super.add(runButton, BorderLayout.SOUTH);
  }
}
