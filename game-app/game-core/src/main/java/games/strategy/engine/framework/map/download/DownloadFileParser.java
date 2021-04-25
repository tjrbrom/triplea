package games.strategy.engine.framework.map.download;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import games.strategy.engine.framework.map.file.system.loader.DownloadedMapsListing;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.triplea.yaml.YamlReader;
import org.triplea.yaml.YamlReader.InvalidYamlFormatException;

/**
 * Utility class to parse an available map list file config file - used to determine which maps are
 * available for download.
 */
@UtilityClass
final class DownloadFileParser {

  enum Tags {
    url,
    version,
    mapName,
    description,
    mapCategory,
    img
  }

  public static List<DownloadFileDescription> parse(final InputStream is) throws IOException {
    try {
      return parseImpl(is);
    } catch (final InvalidYamlFormatException e) {
      throw new IOException(e);
    }
  }

  private static List<DownloadFileDescription> parseImpl(final InputStream is) {
    final List<Map<String, Object>> yamlData = YamlReader.readList(is);

    final List<DownloadFileDescription> downloads = new ArrayList<>();
    final DownloadedMapsListing downloadedMapsListing = DownloadedMapsListing.parseMapFiles();
    yamlData.stream()
        .map(Map.class::cast)
        .forEach(
            yaml -> {
              final String url = (String) checkNotNull(yaml.get(Tags.url.toString()));
              final String description =
                  (String) checkNotNull(yaml.get(Tags.description.toString()));
              final String mapName = (String) checkNotNull(yaml.get(Tags.mapName.toString()));

              final Integer version = (Integer) yaml.get(Tags.version.toString());

              final DownloadFileDescription.MapCategory mapCategory =
                  optEnum(
                      yaml,
                      DownloadFileDescription.MapCategory.class,
                      Tags.mapCategory.toString(),
                      DownloadFileDescription.MapCategory.EXPERIMENTAL);

              final String img = Strings.nullToEmpty((String) yaml.get(Tags.img.toString()));
              downloads.add(
                  DownloadFileDescription.builder()
                      .url(url)
                      .description(description)
                      .mapName(mapName)
                      .version(version)
                      .mapCategory(mapCategory)
                      .img(img)
                      .installLocation(
                          downloadedMapsListing.findMapFolderByName(mapName).orElse(null))
                      .build());
            });
    return downloads;
  }

  private static <T extends Enum<T>> T optEnum(
      final Map<?, ?> jsonObject, final Class<T> type, final String name, final T defaultValue) {
    checkNotNull(jsonObject);
    checkNotNull(type);
    checkNotNull(name);
    checkNotNull(defaultValue);

    final String valueName = Strings.nullToEmpty((String) jsonObject.get(name));
    return valueName.isEmpty() ? defaultValue : Enum.valueOf(type, valueName);
  }
}
