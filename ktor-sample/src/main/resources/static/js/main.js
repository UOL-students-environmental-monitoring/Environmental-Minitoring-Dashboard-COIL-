// Used GPT-5 to assist with dashboard interactions - lines 1-237
import { getDashboardData } from "./api.js";

const elements = {
    status: document.querySelector("#sync-status"),
    siteFilter: document.querySelector("#site-filter"),
    alertFilter: document.querySelector("#alert-filter"),
    mapState: document.querySelector("#map-state"),
    siteMap: document.querySelector("#site-map"),
    alertsState: document.querySelector("#alerts-state"),
    alertsList: document.querySelector("#alerts-list"),
    alertTemplate: document.querySelector("#alert-template"),
    metricSites: document.querySelector("#metric-sites"),
    metricTemp: document.querySelector("#metric-temp"),
    metricMotion: document.querySelector("#metric-motion"),
    metricAlerts: document.querySelector("#metric-alerts"),
};

let dashboardState = {
    sites: [],
    siteReadings: [],
    alerts: [],
};

function setStatus(label, mode = "ready") {
    elements.status.textContent = label;
    elements.status.classList.toggle("is-ready", mode === "ready");
    elements.status.classList.toggle("is-error", mode === "error");
}

function formatSiteName(siteId) {
    return siteId
        .replace(/^herd_/, "")
        .replaceAll("_", " ")
        .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function latestReading(siteReadings) {
    if (!siteReadings.readings.length) {
        return null;
    }

    return siteReadings.readings[siteReadings.readings.length - 1];
}

function selectedSiteId() {
    return elements.siteFilter.value;
}

function selectedAlertSiteId() {
    return elements.alertFilter.value;
}

function filterBySite(items, siteId) {
    if (siteId === "all") {
        return items;
    }
    return items.filter((item) => item.siteId === siteId);
}

function populateFilters() {
    const siteOptions = dashboardState.sites
        .map((site) => `<option value="${site.id}">${formatSiteName(site.id)}</option>`)
        .join("");

    elements.siteFilter.innerHTML = `<option value="all">All herds</option>${siteOptions}`;
    elements.alertFilter.innerHTML = `<option value="all">All herds</option>${siteOptions}`;
}

function mapCoordinate(value, min, max) {
    if (min === max) {
        return 50;
    }
    return 12 + ((value - min) / (max - min)) * 76;
}

function renderMap() {
    elements.siteMap.innerHTML = "";

    const readings = dashboardState.siteReadings
        .filter((entry) => entry.site)
        .filter((entry) => selectedSiteId() === "all" || entry.site.id === selectedSiteId())
        .map((entry) => ({
            site: entry.site,
            reading: latestReading(entry),
        }))
        .filter((entry) => entry.reading);

    if (!dashboardState.sites.length) {
        elements.mapState.textContent = "No monitoring sites are registered yet.";
        return;
    }

    if (!readings.length) {
        elements.mapState.textContent = "No readings available for the selected herd.";
        return;
    }

    elements.mapState.textContent = `${readings.length} herd position${readings.length === 1 ? "" : "s"} shown`;

    const latitudes = readings.map((entry) => entry.reading.latitude);
    const longitudes = readings.map((entry) => entry.reading.longitude);
    const minLat = Math.min(...latitudes);
    const maxLat = Math.max(...latitudes);
    const minLong = Math.min(...longitudes);
    const maxLong = Math.max(...longitudes);

    readings.forEach(({ site, reading }) => {
        const marker = document.createElement("div");
        marker.className = "map-marker";
        marker.classList.toggle("is-alert", reading.alertTriggered);
        marker.style.left = `${mapCoordinate(reading.longitude, minLong, maxLong)}%`;
        marker.style.top = `${100 - mapCoordinate(reading.latitude, minLat, maxLat)}%`;
        marker.setAttribute(
            "aria-label",
            `${formatSiteName(site.id)} at latitude ${reading.latitude}, longitude ${reading.longitude}`,
        );

        const label = document.createElement("span");
        label.textContent = formatSiteName(site.id);
        marker.append(label);
        elements.siteMap.append(marker);
    });
}

function average(values) {
    if (!values.length) {
        return null;
    }
    return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function renderMetrics() {
    const latestReadings = dashboardState.siteReadings
        .map(latestReading)
        .filter(Boolean)
        .filter((reading) => selectedSiteId() === "all" || reading.siteId === selectedSiteId());

    const tempAverage = average(latestReadings.map((reading) => reading.ambientTemperatureC));
    const motionAverage = average(latestReadings.map((reading) => reading.accelMagG));
    const visibleAlerts = filterBySite(dashboardState.alerts, selectedSiteId());

    elements.metricSites.textContent =
        selectedSiteId() === "all" ? String(dashboardState.sites.length) : "1";
    elements.metricTemp.textContent = tempAverage === null ? "--" : `${tempAverage.toFixed(1)} C`;
    elements.metricMotion.textContent = motionAverage === null ? "--" : `${motionAverage.toFixed(2)} g`;
    elements.metricAlerts.textContent = String(visibleAlerts.length);
}

function severityClass(severity) {
    return severity.toLowerCase().includes("critical") ? "is-critical" : "";
}

function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return new Intl.DateTimeFormat("en-GB", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
    }).format(date);
}

function renderAlerts() {
    elements.alertsList.innerHTML = "";

    const alerts = filterBySite(dashboardState.alerts, selectedAlertSiteId()).slice(0, 5);

    if (!dashboardState.alerts.length) {
        elements.alertsState.textContent = "No alerts have been logged.";
        return;
    }

    if (!alerts.length) {
        elements.alertsState.textContent = "No alerts match this herd.";
        return;
    }

    elements.alertsState.textContent = `${alerts.length} recent alert${alerts.length === 1 ? "" : "s"}`;

    alerts.forEach((alert) => {
        const item = elements.alertTemplate.content.firstElementChild.cloneNode(true);
        item.querySelector("[data-alert-message]").textContent = alert.message;
        item.querySelector("[data-alert-meta]").textContent =
            `${formatSiteName(alert.siteId)} - ${alert.parameter} - ${formatDate(alert.timeStamp)}`;

        const severity = item.querySelector("[data-alert-severity]");
        severity.textContent = alert.severity;
        const className = severityClass(alert.severity);
        if (className) {
            severity.classList.add(className);
        }

        elements.alertsList.append(item);
    });
}

function renderDashboard() {
    populateFilters();
    renderMap();
    renderMetrics();
    renderAlerts();
}

function showError(error) {
    setStatus("Error", "error");
    elements.mapState.textContent = error.message;
    elements.mapState.classList.add("is-error");
    elements.alertsState.textContent = "Unable to load alerts.";
    elements.alertsState.classList.add("is-error");
    elements.metricSites.textContent = "--";
    elements.metricTemp.textContent = "--";
    elements.metricMotion.textContent = "--";
    elements.metricAlerts.textContent = "--";
}

async function initialiseDashboard() {
    try {
        dashboardState = await getDashboardData();
        renderDashboard();
        setStatus("Live");
    } catch (error) {
        showError(error);
    }
}

elements.siteFilter.addEventListener("change", () => {
    renderMap();
    renderMetrics();
});

elements.alertFilter.addEventListener("change", renderAlerts);

initialiseDashboard();
