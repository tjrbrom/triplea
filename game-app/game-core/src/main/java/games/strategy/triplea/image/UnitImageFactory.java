package games.strategy.triplea.image;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.Constants;
import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TechTracker;
import games.strategy.triplea.ui.mapdata.MapData;
import games.strategy.triplea.util.UnitCategory;
import games.strategy.triplea.util.UnitOwner;
import games.strategy.ui.Util;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.ImageIcon;
import lombok.Builder;
import lombok.Value;

/** A factory with an image cache for creating unit images. */
public class UnitImageFactory {
  public static final int DEFAULT_UNIT_ICON_SIZE = 48;
  private static final String FILE_NAME_BASE = "units/";

  /**
   * Width of all icons. You probably want getUnitImageWidth(), which takes scale factor into
   * account.
   */
  private final int unitIconWidth;
  /**
   * Height of all icons. You probably want getUnitImageHeight(), which takes scale factor into
   * account.
   */
  private final int unitIconHeight;

  private final int unitCounterOffsetWidth;
  private final int unitCounterOffsetHeight;
  // maps Point -> image
  private final Map<ImageKey, Image> images = new HashMap<>();
  // maps Point -> Icon
  private final Map<String, ImageIcon> icons = new HashMap<>();
  // Scaling factor for unit images
  private final double scaleFactor;
  private final ResourceLoader resourceLoader;
  private final MapData mapData;

  public UnitImageFactory(
      final ResourceLoader resourceLoader, final double unitScale, final MapData mapData) {
    unitIconWidth = mapData.getDefaultUnitWidth();
    unitIconHeight = mapData.getDefaultUnitHeight();
    unitCounterOffsetWidth = mapData.getDefaultUnitCounterOffsetWidth();
    unitCounterOffsetHeight = mapData.getDefaultUnitCounterOffsetHeight();
    this.scaleFactor = unitScale;
    this.resourceLoader = resourceLoader;
    this.mapData = mapData;
  }

  @Value
  @Builder
  public static class ImageKey {
    private final GamePlayer player;
    private final UnitType type;
    private final boolean damaged;
    private final boolean disabled;

    public static ImageKey of(final UnitCategory unit) {
      return ImageKey.builder()
          .player(unit.getOwner())
          .type(unit.getType())
          .damaged(unit.hasDamageOrBombingUnitDamage())
          .disabled(unit.getDisabled())
          .build();
    }

    public static ImageKey of(final UnitOwner holder) {
      return ImageKey.builder().player(holder.getOwner()).type(holder.getType()).build();
    }

    public static ImageKey of(final Unit unit) {
      return ImageKey.builder()
          .player(unit.getOwner())
          .type(unit.getType())
          .damaged(Matches.unitHasTakenSomeBombingUnitDamage().test(unit))
          .disabled(Matches.unitIsDisabled().test(unit))
          .build();
    }

    public String getFullName() {
      return getBaseImageName() + player.getName();
    }

    public String getBaseImageName() {
      final GamePlayer gamePlayer = player;

      StringBuilder name = new StringBuilder(32);
      name.append(type.getName());
      if (!type.getName().endsWith("_hit") && !type.getName().endsWith("_disabled")) {
        if (type.getName().equals(Constants.UNIT_TYPE_AAGUN)) {
          if (TechTracker.hasRocket(gamePlayer) && UnitAttachment.get(type).getIsRocket()) {
            name = new StringBuilder("rockets");
          }
          if (TechTracker.hasAaRadar(gamePlayer) && Matches.unitTypeIsAaForAnything().test(type)) {
            name.append("_r");
          }
        } else if (UnitAttachment.get(type).getIsRocket()
            && Matches.unitTypeIsAaForAnything().test(type)) {
          if (TechTracker.hasRocket(gamePlayer)) {
            name.append("_rockets");
          }
          if (TechTracker.hasAaRadar(gamePlayer)) {
            name.append("_r");
          }
        } else if (UnitAttachment.get(type).getIsRocket()) {
          if (TechTracker.hasRocket(gamePlayer)) {
            name.append("_rockets");
          }
        } else if (Matches.unitTypeIsAaForAnything().test(type)) {
          if (TechTracker.hasAaRadar(gamePlayer)) {
            name.append("_r");
          }
        }
        if (UnitAttachment.get(type).getIsAir()
            && !UnitAttachment.get(type).getIsStrategicBomber()) {
          if (TechTracker.hasLongRangeAir(gamePlayer)) {
            name.append("_lr");
          }
          if (TechTracker.hasJetFighter(gamePlayer)
              && (UnitAttachment.get(type).getAttack(gamePlayer) > 0
                  || UnitAttachment.get(type).getDefense(gamePlayer) > 0)) {
            name.append("_jp");
          }
        }
        if (UnitAttachment.get(type).getIsAir()
            && UnitAttachment.get(type).getIsStrategicBomber()) {
          if (TechTracker.hasLongRangeAir(gamePlayer)) {
            name.append("_lr");
          }
          if (TechTracker.hasHeavyBomber(gamePlayer)) {
            name.append("_hb");
          }
        }
        if (UnitAttachment.get(type).getIsFirstStrike()
            && UnitAttachment.get(type).getCanEvade()
            && (UnitAttachment.get(type).getAttack(gamePlayer) > 0
                || UnitAttachment.get(type).getDefense(gamePlayer) > 0)
            && TechTracker.hasSuperSubs(gamePlayer)) {
          name.append("_ss");
        }
        if ((type.getName().equals(Constants.UNIT_TYPE_FACTORY)
                || UnitAttachment.get(type).getCanProduceUnits())
            && (TechTracker.hasIndustrialTechnology(gamePlayer)
                || TechTracker.hasIncreasedFactoryProduction(gamePlayer))) {
          name.append("_it");
        }
      }
      if (disabled) {
        name.append("_disabled");
      } else if (damaged) {
        name.append("_hit");
      }
      return name.toString();
    }
  }

  /** Set the unitScaling factor. */
  public UnitImageFactory withScaleFactor(final double scaleFactor) {
    return this.scaleFactor == scaleFactor
        ? this
        : new UnitImageFactory(resourceLoader, scaleFactor, mapData);
  }

  /** Return the unit scaling factor. */
  public double getScaleFactor() {
    return scaleFactor;
  }

  /** Return the width of scaled units. */
  public int getUnitImageWidth() {
    return (int) (scaleFactor * unitIconWidth);
  }

  /** Return the height of scaled units. */
  public int getUnitImageHeight() {
    return (int) (scaleFactor * unitIconHeight);
  }

  public int getUnitCounterOffsetWidth() {
    return (int) (scaleFactor * unitCounterOffsetWidth);
  }

  public int getUnitCounterOffsetHeight() {
    return (int) (scaleFactor * unitCounterOffsetHeight);
  }

  public boolean hasImage(final ImageKey imageKey) {
    return images.containsKey(imageKey) || getBaseImageUrl(imageKey).isPresent();
  }

  /** Return the appropriate unit image. */
  public Optional<Image> getImage(final ImageKey imageKey) {
    return Optional.ofNullable(images.get(imageKey))
        .or(
            () ->
                getTransformedImage(imageKey)
                    .map(
                        baseImage -> {
                          // We want to scale units according to the given scale factor.
                          // We use smooth scaling since the images are cached to allow to take our
                          // time in
                          // doing the
                          // scaling.
                          // Image observer is null, since the image should have been guaranteed to
                          // be loaded.
                          final int width = (int) (baseImage.getWidth(null) * scaleFactor);
                          final int height = (int) (baseImage.getHeight(null) * scaleFactor);
                          final Image scaledImage =
                              baseImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                          // Ensure the scaling is completed.
                          Util.ensureImageLoaded(scaledImage);
                          images.put(imageKey, scaledImage);
                          return scaledImage;
                        }));
  }

  public Optional<URL> getBaseImageUrl(final ImageKey imageKey) {
    final String baseImageName = imageKey.getBaseImageName();
    final GamePlayer gamePlayer = imageKey.getPlayer();
    // URL uses '/' not '\'
    final String fileName = FILE_NAME_BASE + gamePlayer.getName() + "/" + baseImageName + ".png";
    final String fileName2 = FILE_NAME_BASE + baseImageName + ".png";
    final URL url = resourceLoader.getResource(fileName, fileName2);
    return Optional.ofNullable(url);
  }

  private Optional<Image> getTransformedImage(final ImageKey imageKey) {
    final GamePlayer gamePlayer = imageKey.getPlayer();
    final UnitType type = imageKey.getType();

    final Optional<URL> imageLocation = getBaseImageUrl(imageKey);
    Image image = null;
    if (imageLocation.isPresent()) {
      image = Toolkit.getDefaultToolkit().getImage(imageLocation.get());
      Util.ensureImageLoaded(image);
      if (needToTransformImage(gamePlayer, type, mapData)) {
        image = convertToBufferedImage(image);
        final Optional<Color> unitColor = mapData.getUnitColor(gamePlayer.getName());
        if (unitColor.isPresent()) {
          final int brightness = mapData.getUnitBrightness(gamePlayer.getName());
          ImageTransformer.colorize(unitColor.get(), brightness, (BufferedImage) image);
        }
        if (mapData.shouldFlipUnit(gamePlayer.getName())) {
          image = ImageTransformer.flipHorizontally((BufferedImage) image);
        }
      }
    }
    return Optional.ofNullable(image);
  }

  private static boolean needToTransformImage(
      final GamePlayer gamePlayer, final UnitType type, final MapData mapData) {
    return !mapData.ignoreTransformingUnit(type.getName())
        && (mapData.getUnitColor(gamePlayer.getName()).isPresent()
            || mapData.shouldFlipUnit(gamePlayer.getName()));
  }

  private static BufferedImage convertToBufferedImage(final Image image) {
    final BufferedImage newImage =
        new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = newImage.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return newImage;
  }

  /**
   * Returns the highlight image for the specified unit.
   *
   * @return The highlight image or empty if no base image is available for the specified unit.
   */
  public Optional<Image> getHighlightImage(final ImageKey imageKey) {
    return getImage(imageKey).map(UnitImageFactory::highlightImage);
  }

  private static Image highlightImage(final Image image) {
    final BufferedImage highlightedImage =
        Util.newImage(image.getWidth(null), image.getHeight(null), true);
    // copy the real image
    final Graphics2D g = highlightedImage.createGraphics();
    g.drawImage(image, 0, 0, null);
    // we want a highlight only over the area that is not clear
    g.setComposite(AlphaComposite.SrcIn);
    g.setColor(new Color(240, 240, 240, 127));
    g.fillRect(0, 0, image.getWidth(null), image.getHeight(null));
    g.dispose();
    return highlightedImage;
  }

  /** Return a icon image for a unit. */
  public Optional<ImageIcon> getIcon(final ImageKey imageKey) {
    final String fullName = imageKey.getFullName();
    if (icons.containsKey(fullName)) {
      return Optional.of(icons.get(fullName));
    }
    final Optional<Image> image = getTransformedImage(imageKey);
    if (image.isEmpty()) {
      return Optional.empty();
    }

    final ImageIcon icon = new ImageIcon(image.get());
    icons.put(fullName, icon);
    return Optional.of(icon);
  }

  public Dimension getImageDimensions(final ImageKey imageKey) {
    final Image baseImage =
        getTransformedImage(imageKey).orElseThrow(() -> new MissingImageException(imageKey));
    final int width = (int) (baseImage.getWidth(null) * scaleFactor);
    final int height = (int) (baseImage.getHeight(null) * scaleFactor);
    return new Dimension(width, height);
  }
}
