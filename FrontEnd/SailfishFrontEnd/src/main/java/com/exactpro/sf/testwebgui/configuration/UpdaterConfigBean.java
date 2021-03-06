/*******************************************************************************
 * Copyright 2009-2019 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.exactpro.sf.testwebgui.configuration;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.center.ISFContext;
import com.exactpro.sf.center.IVersion;
import com.exactpro.sf.embedded.configuration.ServiceStatus;
import com.exactpro.sf.embedded.updater.UpdateService;
import com.exactpro.sf.embedded.updater.ComponentUpdateInfo;
import com.exactpro.sf.embedded.updater.configuration.UpdateServiceSettings;
import com.exactpro.sf.testwebgui.BeanUtil;
import com.exactpro.sf.testwebgui.api.TestToolsAPI;
import com.exactpro.sf.util.DateTimeUtility;

@ManagedBean(name = "updateBean")
@ViewScoped
public class UpdaterConfigBean implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(UpdaterConfigBean.class);

    private static final String[] availableTimeUnits = {
            TimeUnit.MINUTES.name(), TimeUnit.HOURS.name(), TimeUnit.DAYS.name()
    };

    private static final List<String> daysOfWeek = Stream.of(DayOfWeek.values()).map(DayOfWeek::name).collect(Collectors.toList());

    private UpdateServiceSettings settings;
    private List<ComponentInfo> currentVersions;
    private Date from;
    private Date to;

    @PostConstruct
    public void init() {
        ISFContext context = BeanUtil.getSfContext();
        this.settings = context.getUpdateService().getSettings().clone();
        from = parseTime(settings.getFromTime());
        to = parseTime(settings.getToTime());
        this.currentVersions = currentVersions();
    }

    private Date parseTime(String timeValue) {
        if (timeValue != null) {
            return DateTimeUtility.toDate(LocalTime.parse(timeValue, UpdateService.TIME_FORMATTER));
        }
        return null;
    }

    private String formatTime(Date date) {
        if (date == null) {
            return null;
        }
        return DateTimeUtility.toLocalTime(date).format(UpdateService.TIME_FORMATTER);
    }

    public void checkUpdate() {
        try {
            UpdateService updateService = BeanUtil.getSfContext().getUpdateService();
            updateService.checkUpdates();

            for(ComponentInfo componentInfo : currentVersions) {
                componentInfo.setNewVersion(null);
            }

            BeanUtil.addInfoMessage("Updates checked", updateService.isUpdateRequire() ? "Needs update" : "No updates");
        } catch (Exception e) {
            logger.error("Can't check for updates", e);
            BeanUtil.addErrorMessage("ERROR", "Can't check update: " + e.getMessage());
        }
    }

    public String getLastCheckTime() {
        LocalDateTime lastCheckTime = BeanUtil.getSfContext().getUpdateService().getLastCheckTime();
        return lastCheckTime == null ? "" : lastCheckTime.toString();
    }

    public String getUpdateErrorMessage() {
        return BeanUtil.getSfContext().getUpdateService().getUpdateErrorMsg();
    }

    public void restart() {
        try {
            logger.info("Restarting update service...");
            updateSettingsAndRestart();
            logger.info("Update service has been restarted");
            BeanUtil.addInfoMessage("Update service has been restarted", "");
        } catch (Exception e) {
            logger.error("Can't restart update service", e);
            BeanUtil.addErrorMessage("ERROR", "Can't restart update service: " + e.getMessage());
        }
    }

    public void update() {
        try {
            logger.info("Start Sailfish update");
            BeanUtil.getSfContext().getUpdateService().update();
        } catch (Exception e) {
            logger.error("Can't start update", e);
            BeanUtil.addErrorMessage("ERROR", "Can't start update: " + e.getMessage());
        }
    }

    public List<ComponentInfo> getComponentVersions() {
        fillNewVersions();
        return currentVersions;
    }

    public boolean isCriticalError() {
        return BeanUtil.getSfContext().getUpdateService().hasCriticalError();
    }

    private void updateSettingsAndRestart() throws Exception {
        try {
            TestToolsAPI.getInstance().setUpdateServiceSettings(settings);
        } finally {
            settings = BeanUtil.getSfContext().getUpdateService().getSettings().clone();
        }
    }

    public boolean isUpdating() {
        return BeanUtil.getSfContext().getUpdateService().isUpdating();
    }

    public void applySettings() {
        if (isChangesMade()) {
            try {
                validateSettings(settings);
                logger.info("Applying settings...");
                updateSettingsAndRestart();
                BeanUtil.addInfoMessage("Settings applied", "");
                logger.info("Settings for Update service applied");
            } catch (Exception e) {
                logger.error("Can't apply settings", e);
                BeanUtil.addErrorMessage("ERROR", "Can't apply settings: " + e.getMessage());
            }
        } else {
            BeanUtil.addInfoMessage("No change to apply", "");
        }
    }

    private void validateSettings(UpdateServiceSettings settings) {
        if (settings.isEnableAutoUpdate()) {
            LocalTime fromTime = DateTimeUtility.toLocalTime(from);
            LocalTime toTime = DateTimeUtility.toLocalTime(to);
            if (!fromTime.isBefore(toTime)) {
                throw new IllegalArgumentException("'From' must be before 'To'");
            }
        }
    }

    private boolean isChangesMade() {
        UpdateServiceSettings currentSettings = BeanUtil.getSfContext().getUpdateService().getSettings();
        return !currentSettings.equals(settings);
    }

    private void fillNewVersions() {
        UpdateService updateService = BeanUtil.getSfContext().getUpdateService();
        if (updateService.isConnected() && updateService.isUpdateRequire()) {
            currentVersions = currentVersions();
            updateNewVersions(updateService, currentVersions);

            processNewComponents(updateService, currentVersions);

            markRemovedComponents(updateService, currentVersions);

        }
    }

    private void markRemovedComponents(UpdateService updateService, List<ComponentInfo> currentVersions) {
        for (ComponentUpdateInfo removedComponent : updateService.getRemovedComponents()) {
            currentVersions.stream()
                    .filter(info -> info.getName().equals(removedComponent.getName()))
                    .findFirst()
                    .ifPresent(info -> info.setRemoved(true));
        }
    }

    private void processNewComponents(UpdateService updateService, List<ComponentInfo> currentVersions) {
        for (ComponentUpdateInfo addedComponent : updateService.getAddedComponents()) {
            ComponentInfo info = new ComponentInfo(addedComponent.getName(), null);
            info.setNewVersion(addedComponent.getVersion());
            info.setAdded(true);
            currentVersions.add(info);
        }
    }

    private void updateNewVersions(UpdateService updateService, List<ComponentInfo> currentVersions) {
        for(ComponentUpdateInfo componentUpdateInfo : updateService.getComponentUpdateInfos()) {
            currentVersions.stream()
                    .filter(componentInfo -> componentInfo.getName().equals(componentUpdateInfo.getName()))
                    .findFirst()
                    .ifPresent(componentInfo -> componentInfo.setNewVersion(componentUpdateInfo.getVersion()));
        }
    }

    private List<ComponentInfo> currentVersions() {
        return BeanUtil.getSfContext().getUpdateService().getCurrentComponents().stream()
                .map(info -> new ComponentInfo(info.getName(), info.getVersion()))
                .collect(Collectors.toList());
    }

    public ServiceStatus getServiceStatus() {
        return BeanUtil.getSfContext().getUpdateService().getStatus();
    }

    public boolean isNeedsUpdate() {
        return BeanUtil.getSfContext().getUpdateService().isUpdateRequire();
    }

    public String getErrorMessage() {
        return BeanUtil.getSfContext().getUpdateService().getErrorMsg();
    }

    public String[] getAvailableTimeUnits() {
        return availableTimeUnits;
    }

    public UpdateServiceSettings getSettings() {
        return settings;
    }

    public void setSettings(UpdateServiceSettings settings) {
        this.settings = settings;
    }

    public List<String> getDaysOfWeek() {
        return daysOfWeek;
    }

    public String getTimePattern() {
        return UpdateService.TIME_PATTERN;
    }

    public Date getFrom() {
        return from;
    }

    public void setFrom(Date from) {
        this.from = from;
        settings.setFromTime(formatTime(from));
    }

    public Date getTo() {
        return to;
    }

    public void setTo(Date to) {
        this.to = to;
        settings.setToTime(formatTime(to));
    }

    public static class ComponentInfo {

        private final String name;

        private final String currentVersion;

        private String newVersion;

        private boolean removed;

        private boolean added;

        public ComponentInfo(IVersion version) {
            this.name = version.getArtifactName();
            this.currentVersion = version.getArtifactVersion();
        }

        public ComponentInfo(String name, String currentVersion) {
            this.name = name;
            this.currentVersion = currentVersion;
        }

        public String getName() {
            return name;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getNewVersion() {
            return newVersion;
        }

        public void setNewVersion(String newVersion) {
            this.newVersion = newVersion;
        }

        public boolean isRemoved() {
            return removed;
        }

        public void setRemoved(boolean removed) {
            this.removed = removed;
        }

        public boolean isAdded() {
            return added;
        }

        public void setAdded(boolean added) {
            this.added = added;
        }
    }
}
