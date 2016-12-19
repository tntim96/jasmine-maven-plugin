package com.github.searls.jasmine.runner;

import org.openqa.selenium.WebDriver;

public interface WebDriverCallback {
  void execute(WebDriver driver);
}
