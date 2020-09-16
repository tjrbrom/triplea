package tools.map.making;

import static com.google.common.base.Preconditions.checkState;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.java.Log;
import tools.image.FileOpen;
import tools.image.MapFolderLocationSystemProperty;

/** Takes an image and shrinks it. Used for making small images. */
@Log
public final class ImageShrinker {
  private File mapFolderLocation = null;

  private ImageShrinker() {}

  /**
   * Runs the image shrinker tool.
   *
   * @throws IllegalStateException If not invoked on the EDT.
   */
  public static void run() {
    checkState(SwingUtilities.isEventDispatchThread());

    try {
      new ImageShrinker().runInternal();
    } catch (final IOException e) {
      log.log(Level.SEVERE, "failed to run image shrinker", e);
    }
  }

  private void runInternal() throws IOException {
    mapFolderLocation = MapFolderLocationSystemProperty.read();
    JOptionPane.showMessageDialog(
        null,
        new JLabel(
            "<html>"
                + "This is the ImageShrinker, it will create a smallMap.jpeg file for you. "
                + "<br>Put in your base map or relief map, and it will spit out a small "
                + "scaled copy of it."
                + "<br>Please note that the quality of the image will be worse than if you use a "
                + "real painting program."
                + "<br>So we suggest you instead shrink the image with paint.net or photoshop or "
                + "gimp, etc, then clean it up before saving."
                + "</html>"));
    final File mapFile =
        new FileOpen("Select The Large Image", mapFolderLocation, ".gif", ".png").getFile();
    if (mapFile == null || !mapFile.exists()) {
      throw new IllegalStateException(mapFile + "File does not exist");
    }
    if (mapFolderLocation == null) {
      mapFolderLocation = mapFile.getParentFile();
    }
    final String input = JOptionPane.showInputDialog(null, "Select scale");
    final float scale = Float.parseFloat(input);
    final Image baseImg = ImageIO.read(mapFile);
    final int thumbWidth = (int) (baseImg.getWidth(null) * scale);
    final int thumbHeight = (int) (baseImg.getHeight(null) * scale);
    // based on code from
    // http://www.geocities.com/marcoschmidt.geo/java-save-jpeg-thumbnail.html
    // draw original image to thumbnail image object and scale it to the new size on-the-fly
    final BufferedImage thumbImage =
        new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics2D = thumbImage.createGraphics();
    graphics2D.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    graphics2D.drawImage(baseImg, 0, 0, thumbWidth, thumbHeight, null);
    // save thumbnail image to OUTFILE
    final File file =
        new File(new File(mapFile.getPath()).getParent() + File.separatorChar + "smallMap.jpeg");
    try (ImageOutputStream out = new FileImageOutputStream(file)) {
      final ImageWriter encoder = ImageIO.getImageWritersByFormatName("JPEG").next();
      final JPEGImageWriteParam param = new JPEGImageWriteParam(null);
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality((float) 1.0);
      encoder.setOutput(out);
      encoder.write(null, new IIOImage(thumbImage, null, null), param);
    }
    log.info("Image successfully written to " + file.getPath());
  }
}
