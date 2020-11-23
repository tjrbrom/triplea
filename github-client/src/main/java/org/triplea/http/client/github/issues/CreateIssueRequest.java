package org.triplea.http.client.github.issues;

import com.google.common.base.Ascii;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.triplea.http.client.HttpClientConstants;

/** Represents request data to create a github issue. */
@ToString
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateIssueRequest {
  private String title;
  private String body;
  private String[] labels;

  public String getTitle() {
    return title == null
        ? null
        : Ascii.truncate(title, HttpClientConstants.TITLE_MAX_LENGTH, "...");
  }

  public String getBody() {
    return body == null
        ? null
        : Ascii.truncate(body, HttpClientConstants.REPORT_BODY_MAX_LENGTH, "...");
  }
}
