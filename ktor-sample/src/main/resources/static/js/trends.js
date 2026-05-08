// =========================================================================
// trends.js
// All JavaScript logic for the Trends page
// Placed in: resources/static/js/trends.js
// Loaded from: templates/thymeleaf/trends.html (deferred, so it runs after DOM is ready)
//
// What this file does:
//   1. Fetches the list of sites from /api/sites and fills the site dropdown
//   2. Fetches sensor readings from /api/readings for the selected site,
//      month, and view (last 7 days or full month)
//   3. Draws a Chart.js line chart from the fetched data
//   4. Re-fetches and redraws whenever the user changes any control
//   5. Lets the user download the visible data as a CSV or HTML file
// =========================================================================

// -----------------------------------------------------------------------
// Global state - tracks whatever the user has currently selected
// -----------------------------------------------------------------------
let chosenSite   = "";
let chosenMonth  = "2023-12"; // default to the most recent month in the dataset
let chosenView   = "7days";   // "7days" or "month" - only applies when Dec 2023 is chosen
let chosenParam  = "temp";    // "temp" (temperature) or "activity" (accelerometer)
let chosenFormat = "csv";     // "csv" or "html" - only used when Download is clicked

// holds the active Chart.js instance so we can destroy it before creating a new one
// (creating a second chart on the same canvas causes visual glitches)
let chartJsInstance = null;

// -----------------------------------------------------------------------
// MONTH_DATA
// A lookup table from "YYYY-MM" value to display info and calendar details.
// "days" is how many days that month has - needed to build the end-of-month date.
// -----------------------------------------------------------------------
const MONTH_DATA = {
    "2023-01": { label: "January 2023",   year: 2023, month: 1,  days: 31 },
    "2023-02": { label: "February 2023",  year: 2023, month: 2,  days: 28 },
    "2023-03": { label: "March 2023",     year: 2023, month: 3,  days: 31 },
    "2023-04": { label: "April 2023",     year: 2023, month: 4,  days: 30 },
    "2023-05": { label: "May 2023",       year: 2023, month: 5,  days: 31 },
    "2023-06": { label: "June 2023",      year: 2023, month: 6,  days: 30 },
    "2023-07": { label: "July 2023",      year: 2023, month: 7,  days: 31 },
    "2023-08": { label: "August 2023",    year: 2023, month: 8,  days: 31 },
    "2023-09": { label: "September 2023", year: 2023, month: 9,  days: 30 },
    "2023-10": { label: "October 2023",   year: 2023, month: 10, days: 31 },
    "2023-11": { label: "November 2023",  year: 2023, month: 11, days: 30 },
    "2023-12": { label: "December 2023",  year: 2023, month: 12, days: 31 }
};

// only December 2023 (the most recent month) gets the "last 7 days" toggle
const LATEST_MONTH = "2023-12";

// -----------------------------------------------------------------------
// formatDateStr(year, month, day, hour, minute, second)
// builds the "YYYY-MM-DDTHH:MM:SS" string that Ktor's LocalDateTime.parse() expects.
// padStart(2, "0") adds a leading zero to single-digit numbers - e.g. 3 → "03"
// -----------------------------------------------------------------------
function formatDateStr(year, month, day, hour, minute, second) {
    const mm  = String(month).padStart(2, "0");
    const dd  = String(day).padStart(2, "0");
    const hh  = String(hour).padStart(2, "0");
    const min = String(minute).padStart(2, "0");
    const ss  = String(second).padStart(2, "0");
    return `${year}-${mm}-${dd}T${hh}:${min}:${ss}`;
}

// -----------------------------------------------------------------------
// getDateRange()
// works out the "from" and "to" timestamps to pass to /api/readings,
// based on the currently chosen month and view.
// returns an object with two string properties: { from, to }
// -----------------------------------------------------------------------
function getDateRange() {
    const info = MONTH_DATA[chosenMonth];

    // start of month: e.g. "2023-12-01T00:00:00"
    const monthStart = formatDateStr(info.year, info.month, 1,        0,  0,  0);
    // end of month:   e.g. "2023-12-31T23:59:59"
    const monthEnd   = formatDateStr(info.year, info.month, info.days, 23, 59, 59);

    // the 7-day view is only available for the most recent month (December 2023)
    if (chosenMonth === LATEST_MONTH && chosenView === "7days") {
        // "last 7 days" means the final 7 days of December: Dec 25 → Dec 31
        // subtracting 6 from the last day gives us a 7-day window inclusive of both ends
        const firstOfLast7 = info.days - 6;
        const sevenDayStart = formatDateStr(info.year, info.month, firstOfLast7, 0, 0, 0);
        return { from: sevenDayStart, to: monthEnd };
    }

    // every other month always shows the full month
    return { from: monthStart, to: monthEnd };
}

// -----------------------------------------------------------------------
// updateTimeToggleVisibility()
// Shows the "Last 7 days / Full month" toggle only when December 2023 is selected.
// For all other months the toggle is hidden and the view resets to full month.
// -----------------------------------------------------------------------
function updateTimeToggleVisibility() {
    const toggleGroup = document.getElementById("time-toggle-group");

    if (chosenMonth === LATEST_MONTH) {
        // make the toggle visible again for December 2023
        toggleGroup.style.display = "";
    } else {
        // hide the toggle - past months always show the whole month
        toggleGroup.style.display = "none";
        chosenView = "month";

        // reset the button highlight so December looks correct when the user returns to it
        document.querySelectorAll("#time-toggle button").forEach(function(btn) {
            btn.classList.toggle("selected", btn.dataset.view === "month");
        });
    }
}

// -----------------------------------------------------------------------
// loadSites()
// called once when the page first opens.
// fetches all available sites from the API, fills the dropdown,
// then triggers the first chart draw automatically.
// -----------------------------------------------------------------------
async function loadSites() {
    // fetch() sends an HTTP GET request; await pauses until the response arrives
    const response = await fetch("/api/sites");

    // .json() reads the response body and converts the JSON text into a JavaScript array
    // e.g. [{ id: "herd_cattle_A", description: "..." }, ...]
    const sites = await response.json();

    const siteSelect = document.getElementById("site-select");
    // clear the "Loading sites..." placeholder before adding real options
    siteSelect.innerHTML = "";

    // build one <option> element for each site returned by the API
    sites.forEach(function(site) {
        const option = document.createElement("option");
        option.value       = site.id; // the value sent to the API as the "site" query param
        option.textContent = site.id; // the text the user actually reads in the dropdown
        siteSelect.appendChild(option);
    });

    // auto-select the first site so the chart is shown immediately on page load
    if (sites.length > 0) {
        chosenSite = sites[0].id;
        loadChartReadings();
    }
}

// -----------------------------------------------------------------------
// loadChartReadings()
// fetches sensor readings from the API for the current selections,
// then passes the data to drawChart().
// called every time the user changes any control.
// -----------------------------------------------------------------------
async function loadChartReadings() {
    // show the loading hint while we wait for the network response
    document.getElementById("loading-message").style.display = "block";
    document.getElementById("error-message").style.display   = "none";

    // work out the from/to dates based on the chosen month and view
    const { from, to } = getDateRange();

    // encodeURIComponent() makes the colons and hyphens in the datetime safe in a URL
    const url = `/api/readings?site=${chosenSite}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;

    try {
        const response = await fetch(url);

        // response.ok is true for HTTP 200-299 (success), false for 400/500 errors
        if (!response.ok) {
            throw new Error("Server returned status " + response.status);
        }

        // parse the JSON body into an array of ReadingDTO objects
        const readings = await response.json();

        document.getElementById("loading-message").style.display = "none";
        drawChart(readings);

    } catch (error) {
        // log to the browser console to help with debugging
        console.error("Chart failed to load data:", error);
        document.getElementById("loading-message").style.display = "none";
        document.getElementById("error-message").style.display   = "block";
    }
}

// -----------------------------------------------------------------------
// drawChart(readings)
// takes an array of ReadingDTO objects and draws a Chart.js line graph.
// called by loadChartReadings() once data arrives from the API.
// -----------------------------------------------------------------------
function drawChart(readings) {
    let yValues;
    let yLabel;

    if (chosenParam === "temp") {
        // ambientTemperatureC is the field name from ReadingDTO in DataTransferObjects.kt
        yValues = readings.map(function(r) { return r.ambientTemperatureC; });
        yLabel  = "Ambient Temperature (°C)";
    } else {
        // accelMagG = accelerometer magnitude in g-force, a proxy for animal movement
        yValues = readings.map(function(r) { return r.accelMagG; });
        yLabel  = "Activity (accel_mag_g)";
    }

    // use just the date part of each timestamp as the X-axis labels
    // e.g. "2023-12-25T10:30:00" → "2023-12-25" via .slice(0, 10)
    const xLabels = readings.map(function(r) {
        return r.timeStamp.slice(0, 10);
    });

    // destroy the previous chart before creating a new one
    // (Chart.js will throw warnings if you draw on a canvas that already has a chart)
    if (chartJsInstance !== null) {
        chartJsInstance.destroy();
        chartJsInstance = null;
    }

    const ctx = document.getElementById("trends-chart").getContext("2d");

    // build a descriptive X-axis title from the current selection
    const info   = MONTH_DATA[chosenMonth];
    const xTitle = (chosenMonth === LATEST_MONTH && chosenView === "7days")
        ? "Last 7 days of " + info.label
        : info.label;

    chartJsInstance = new Chart(ctx, {
        type: "line",
        data: {
            labels:   xLabels,
            datasets: [{
                label:           yLabel,
                data:            yValues,
                borderColor:     "rgb(130, 60, 200)",
                backgroundColor: "rgba(130, 60, 200, 0.1)",
                borderWidth:     2,
                pointRadius:     0, // hides dots - too many readings to show individual points
                tension:         0.3 // slight smooth curve; 0 = sharp lines, 1 = very wavy
            }]
        },
        options: {
            responsive:          true,
            maintainAspectRatio: false, // lets the CSS height rule control the chart's height

            plugins: {
                legend:  { display: true, position: "top" },
                tooltip: {
                    mode:      "index",  // show tooltip for the whole vertical column, not just a dot
                    intersect: false
                }
            },

            scales: {
                x: {
                    title: { display: true, text: xTitle },
                    ticks: { maxTicksLimit: 10 } // keeps X-axis labels from crowding together
                },
                y: {
                    title: { display: true, text: yLabel }
                }
            }
        }
    });
}

// =========================================================================
// Download functionality
// The user can save the currently displayed data to their device as CSV or HTML.
// We use the same /api/readings endpoint as the chart, then build the file
// client-side with the JavaScript Blob API (no server-side file generation needed).
// =========================================================================

// buildFilename(extension) - makes a descriptive filename for the download
// e.g. "readings_herd_cattle_A_2023-12_temp.csv"
function buildFilename(extension) {
    return `readings_${chosenSite}_${chosenMonth}_${chosenParam}.${extension}`;
}

// downloadCsv(readings) - converts the readings array into CSV text and saves it
function downloadCsv(readings) {
    // pick the right field name and column header depending on the chosen parameter
    const valueKey = chosenParam === "temp" ? "ambientTemperatureC" : "accelMagG";
    const valueCol = chosenParam === "temp" ? "temperature_c"       : "activity_accel_mag_g";

    // .join(",") puts a comma between every item in the array - builds one CSV line
    let csv = ["timestamp", "site_id", valueCol].join(",") + "\n";

    readings.forEach(function(r) {
        csv += [r.timeStamp, r.siteId, r[valueKey]].join(",") + "\n";
    });

    // Blob = "Binary Large Object" - a chunk of in-memory data, here our CSV text
    triggerDownload(new Blob([csv], { type: "text/csv" }), buildFilename("csv"));
}

// downloadHtml(readings) - wraps the readings in a styled HTML table and saves it
function downloadHtml(readings) {
    const valueKey  = chosenParam === "temp" ? "ambientTemperatureC" : "accelMagG";
    const valueCol  = chosenParam === "temp" ? "Temperature (°C)"    : "Activity (accel_mag_g)";
    const info      = MONTH_DATA[chosenMonth];

    // build one <tr> table row per reading, then join them all together
    const tableRows = readings.map(function(r) {
        return `        <tr><td>${r.timeStamp}</td><td>${r.siteId}</td><td>${r[valueKey]}</td></tr>`;
    }).join("\n");

    // a fully self-contained HTML file the user can open in any browser
    const html =
`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <title>Readings Export - ${info.label}</title>
    <style>
        body  { font-family: Arial, sans-serif; padding: 20px; }
        h1    { font-size: 1.2rem; margin-bottom: 12px; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ccc; padding: 8px 12px; text-align: left; }
        th { background-color: #2c3e50; color: white; }
        tr:nth-child(even) { background-color: #f0f2f5; }
    </style>
</head>
<body>
    <h1>${valueCol} readings - ${info.label} - Site: ${chosenSite}</h1>
    <table>
        <thead>
            <tr><th>Timestamp</th><th>Site</th><th>${valueCol}</th></tr>
        </thead>
        <tbody>
${tableRows}
        </tbody>
    </table>
</body>
</html>`;

    triggerDownload(new Blob([html], { type: "text/html" }), buildFilename("html"));
}

// triggerDownload(blob, filename)
// creates a temporary hidden <a> link, points it at the blob, and clicks it.
// this is the standard browser trick for saving a file without a server download URL.
function triggerDownload(blob, filename) {
    // createObjectURL() turns the blob into a temporary URL like "blob:http://..."
    const url  = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href     = url;
    link.download = filename;

    // the link must be in the DOM for Firefox to recognise the download attribute
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    // revokeObjectURL() frees the browser memory used by the blob URL
    // without this it would linger in memory until the page is closed
    URL.revokeObjectURL(url);
}

// downloadReadings()
// the main download handler: fetches data, then routes to CSV or HTML builder
async function downloadReadings() {
    const btn = document.getElementById("download-btn");
    btn.disabled    = true;
    btn.textContent = "Downloading...";

    const { from, to } = getDateRange();
    const url = `/api/readings?site=${chosenSite}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;

    try {
        const response = await fetch(url);
        if (!response.ok) throw new Error("Server returned status " + response.status);

        const readings = await response.json();

        if (chosenFormat === "csv") {
            downloadCsv(readings);
        } else {
            downloadHtml(readings);
        }

    } catch (error) {
        console.error("Download failed:", error);
        alert("Download failed - please check your connection and try again.");

    } finally {
        // re-enable the button whether the download succeeded or failed
        btn.disabled    = false;
        btn.textContent = "Download";
    }
}

// =========================================================================
// Event Listeners
// Connect each HTML control to its handler function.
// =========================================================================

// site dropdown - re-fetch when the user picks a different herd
document.getElementById("site-select").addEventListener("change", function(event) {
    chosenSite = event.target.value;
    loadChartReadings();
});

// month dropdown - update the date range, show/hide the toggle, then redraw
document.getElementById("month-select").addEventListener("change", function(event) {
    chosenMonth = event.target.value;
    updateTimeToggleVisibility();
    loadChartReadings();
});

// time toggle (Last 7 days / Full month) - only reachable when December 2023 is shown
document.getElementById("time-toggle").addEventListener("click", function(event) {
    const clicked = event.target;
    // ignore clicks on the surrounding div - only respond to actual button clicks
    if (clicked.tagName !== "BUTTON") return;

    // visually highlight the clicked button and un-highlight the other one
    document.querySelectorAll("#time-toggle button").forEach(function(btn) {
        btn.classList.remove("selected");
    });
    clicked.classList.add("selected");

    // dataset.view reads the data-view="7days" or data-view="month" HTML attribute
    chosenView = clicked.dataset.view;
    loadChartReadings();
});

// parameter dropdown - change which sensor field is drawn on the chart
document.getElementById("param-select").addEventListener("change", function(event) {
    chosenParam = event.target.value;
    loadChartReadings();
});

// format dropdown - just store the selection; it is used when Download is clicked
document.getElementById("format-select").addEventListener("change", function(event) {
    chosenFormat = event.target.value;
});

// download button - fetch data for the current selection and save it as a file
document.getElementById("download-btn").addEventListener("click", function() {
    if (!chosenSite) {
        alert("Please select a site before downloading.");
        return;
    }
    downloadReadings();
});

// =========================================================================
// Page load - fetch sites, fill the dropdown, then draw the initial chart
// =========================================================================
loadSites();