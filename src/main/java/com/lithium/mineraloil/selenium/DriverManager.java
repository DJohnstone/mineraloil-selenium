package com.lithium.mineraloil.selenium;

import com.lithium.mineraloil.selenium.browsers.BrowserType;
import com.lithium.mineraloil.selenium.elements.JavascriptHelper;
import com.lithium.mineraloil.waiters.WaiterImpl;
import lombok.Data;
import lombok.Getter;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotVisibleException;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.MoveTargetOutOfBoundsException;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class DriverManager {
    private static final Logger logger = LoggerFactory.getLogger(DriverManager.class);
    private static final String DEFAULT_BROWSER_ID = "main-" + Thread.currentThread().getId();
    private static final String CLEANUP_BROWSER_ID = "cleanup-" + Thread.currentThread().getId();

    @Getter
    private static String defaultWindowHandle;
    @Getter
    private static Map<Long, Stack<DriverInstance>> drivers = new HashMap<>();

    @Data
    protected static class DriverInstance {
        protected WebDriver driver;
        protected String id;
        protected BrowserType browserType;

        public DriverInstance(BrowserType browserType, String id) {
            this.browserType = browserType;
            startDriver(browserType, id);
        }

        private void startDriver(BrowserType browserType, String id) {
            driver = browserType.create();
            logger.info(String.format("Starting driver %s: %s", id, getDriver().getWindowHandle()));
            defaultWindowHandle = getDriver().getWindowHandle();
            this.id = id;
        }

    }

    public static boolean isDriverStarted() {
        return getCurrentDriverInstance() != null;
    }

    public static WebDriver getCurrentWebDriver() {
        DriverInstance currentDriver = getCurrentDriverInstance();
        if (currentDriver == null) {
            logger.warn(String.format("Driver not found for thread %s, starting new driver", Thread.currentThread().getId()));
            startDriver(currentDriver.getBrowserType());
            currentDriver = getCurrentDriverInstance();
        }
        return currentDriver.getDriver();
    }

    protected static DriverInstance getCurrentDriverInstance() {
        Stack<DriverInstance> driverStack = drivers.get(Thread.currentThread().getId());
        return (driverStack == null || driverStack.empty()) ? null : driverStack.peek();
    }

    public static void gotoURL(String url, BrowserType browserType) {
        try {
            getCurrentWebDriver().get(url);
        } catch (UnreachableBrowserException e) {
            logger.info("WebDriver died...attempting restart: " + getCurrentDriverInstance().getId());
            removeDriverInstance(getCurrentDriverInstance().getId());
            startDriver(browserType);
            getCurrentWebDriver().get(url);
        }
    }

    public static void startCleanupDriver(BrowserType browserType) {
        startDriver(CLEANUP_BROWSER_ID, browserType);
    }

    public static void startDriver(BrowserType browserType) {
        startDriver(DEFAULT_BROWSER_ID, browserType);
    }

    public static void startDriver(String id, BrowserType browserType) {
        if (!isDriverStarted()) addExpectedWaiterExceptions();
        DriverInstance driverInstance = new DriverInstance(browserType, id);
        putDriver(driverInstance);
        if (driverInstance.getBrowserType().equals(BrowserType.CHROME)) {
            maximizeWindow();
        }
        logger.info(String.format("Starting driver %s: %s", id, driverInstance.getDriver().toString()));
    }

    private static void addExpectedWaiterExceptions() {
        WaiterImpl.addExpectedException(StaleElementReferenceException.class);
        WaiterImpl.addExpectedException(NoSuchElementException.class);
        WaiterImpl.addExpectedException(ElementNotVisibleException.class);
        WaiterImpl.addExpectedException(WebDriverException.class);
        WaiterImpl.addExpectedException(MoveTargetOutOfBoundsException.class);
        WaiterImpl.addExpectedException(NullPointerException.class);
    }

    public static void useDriver(String driver) {
        getDriverInstance(driver);
    }

    public static void useDefaultDriver() {
        getDriverInstance(DEFAULT_BROWSER_ID);
    }

    public static void stopDriver(String id) {
        logger.info("Closing driver: " + id);
        getDriverInstance(id).getDriver().close();
        drivers.get(Thread.currentThread().getId()).pop();
    }

    public static void stopCleanupDriver() {
        stopDriver(CLEANUP_BROWSER_ID);
    }

    private static WebDriver instantiateDriver(BrowserType browserType) {
        WebDriver driver = browserType.create();
        driver.manage().window().maximize();
        return driver;
    }

    public static void switchWindow() {
        List<String> windowHandles = new ArrayList<>(getCurrentWebDriver().getWindowHandles());
        getCurrentWebDriver().switchTo().window(windowHandles.get(windowHandles.size() - 1));
    }

    public static void closeWindow() {
        List<String> windowHandles = new ArrayList<>(getCurrentWebDriver().getWindowHandles());
        getCurrentWebDriver().switchTo().window(windowHandles.get(windowHandles.size() - 1));
        getCurrentWebDriver().close();
        switchWindow();
    }

    public static void openNewWindow(String url) {
        JavascriptHelper.openNewWindow(url);
        DriverManager.switchWindow();
    }

    public static void maximizeWindow() {
        java.awt.Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        org.openqa.selenium.Point position = new org.openqa.selenium.Point(0, 0);
        getCurrentWebDriver().manage().window().maximize();
        getCurrentWebDriver().manage().window().setPosition(position);
        org.openqa.selenium.Dimension maximizedScreenSize =
                new org.openqa.selenium.Dimension((int) screenSize.getWidth(), (int) screenSize.getHeight());
        getCurrentWebDriver().manage().window().setSize(maximizedScreenSize);
    }


    public static void closeBrowser() {
        while (drivers.get(Thread.currentThread().getId()).size() > 1) {
            DriverInstance driverInstance = drivers.get(Thread.currentThread().getId()).pop();
            logger.info("Closing driver for thread id " + Thread.currentThread().getId());
            try {
                driverInstance.getDriver().close();
            } catch (WebDriverException e) {
                logger.info(String.format("There was an ignored exception closing the web driver : %s", e));
            }
        }
    }

    public static void quitAllBrowsers() {
        for (Long threadId : drivers.keySet()) {
            if (!drivers.get(threadId).empty()) {
                DriverInstance driverInstance = drivers.get(threadId).pop();
                try {
                    driverInstance.getDriver().close();
                } catch (WebDriverException e) {
                    logger.info(String.format("There was an ignored exception closing the web driver : %s", e));
                }
                logger.info("Closing driver for thread id " + threadId);
            }
        }
    }

    public static boolean isAlertPresent() {
        try {
            getCurrentWebDriver().switchTo().alert();
            return true;
        } catch (NoAlertPresentException e) {
            return false;
        }
    }

    public static String getAlertText() {
        return getCurrentWebDriver().switchTo().alert().getText();
    }

    public static void acceptAlert() {
        getCurrentWebDriver().switchTo().alert().accept();
    }

    private static void putDriver(DriverInstance driverInstance) {
        Stack<DriverInstance> driverInstanceStack;
        if (drivers.get(Thread.currentThread().getId()) == null) {
            driverInstanceStack = new Stack<>();
            driverInstanceStack.push(driverInstance);
            drivers.put(Thread.currentThread().getId(), driverInstanceStack);
        } else {
            drivers.get(Thread.currentThread().getId()).push(driverInstance);
        }
    }

    private static DriverInstance getDriverInstance(String driverId) {
        // Remove all driver instances
        DriverInstance driverInstance = removeDriverInstance(driverId);
        if (driverInstance == null) {
            throw new DriverNotFoundException(String.format("Driver with id %s was not found", driverId));
        }

        // push it back on the top of the stack
        drivers.get(Thread.currentThread().getId()).push(driverInstance);

        // return it
        return drivers.get(Thread.currentThread().getId()).peek();
    }

    private static DriverInstance removeDriverInstance(String driverId) {
        // Iterate over stack and remove all occurences of DriverInstance with this id
        Iterator<DriverInstance> iter = drivers.get(Thread.currentThread().getId()).iterator();
        DriverInstance driverInstance = null;
        while (iter.hasNext()) {
            driverInstance = iter.next();
            logger.info("driverInstance " + driverInstance.getId());
            if (driverInstance.getId().equals(driverId)) {
                while (drivers.get(Thread.currentThread().getId()).remove(driverInstance)) {
                    // removing all possible driver instances of this id
                }
                break;
            }
        }
        return driverInstance;
    }

    public static String getText() {
        return getHTMLElement().getText();
    }

    public static String getHtml() {
        return getCurrentWebDriver().getPageSource();
    }

    private static WebElement getHTMLElement() {
        return getCurrentWebDriver().findElement(By.xpath("//html"));
    }
}