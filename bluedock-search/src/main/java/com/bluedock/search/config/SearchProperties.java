package com.bluedock.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bluedock.search")
public class SearchProperties {
  /** mysql | docs | opensearch */
  private String engine = "docs";

  private final Opensearch opensearch = new Opensearch();

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  public Opensearch getOpensearch() {
    return opensearch;
  }

  public static class Opensearch {
    private boolean enabled = false;
    private String url = "http://127.0.0.1:19200";
    private String index = "bluedock-search";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getIndex() {
      return index;
    }

    public void setIndex(String index) {
      this.index = index;
    }
  }
}
