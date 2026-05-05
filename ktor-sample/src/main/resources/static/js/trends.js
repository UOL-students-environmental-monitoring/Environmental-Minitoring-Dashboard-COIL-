// =========================================================================
// trends.js
// All JavaScript logic for the Trends page
// Placed in: resources/static/js/trends.js
// Loaded from: templates/thymeleaf/trends.html (at the bottom of <body>)
//
// What this file does:
//   1. Fetches the list of sites from /api/sites and occupies the dropdown
//   2. Fetches sensor readings from /api/readings for the selected site
//      and time range
//   3. Draws a Chart.js line chart with fetched data
//   4. Re-fetches and redraws whenever the user changes a control
// =========================================================================

// global variables - hold what the user has currently chosen
let chosenSite = "";
let chosenDays = 7;
let chosenParam ="temp";

// stores Chart.js object so we can create new instance for every parameter
let chartJsInstance = null;

// -------------------------------------------------------
// Load the site list
// called when the page first loads
// occupies the dropdown menus and displays initial graph
// -------------------------------------------------------
async function loadSites(){
    try {
        // fetch() sends an HTTP GET request and await waits for network response
        const response = await fetch("/api/sites");
        if (!response.ok) {
            throw new Error("Server returned status " + response.status);
        }

        // reads the body and converts JSON text into an array of Java objects
        // e.g. [{ id: "herd_cattle_A", ... }, ...]
        const sites = await response.json();

        const choice = document.getElementById("site-select");
        choice.innerHTML = "";

        // loop through all sites and create option element for it
        sites.forEach(function(site) {
            const option = document.createElement("option");
            option.value       = site.id; // the value the API receives in the request
            option.textContent = site.id; // the text displayed to the user
            choice.appendChild(option);
        });

        // defaults to first site so graph can be displayed immediately
        if ( sites.length > 0 ){
            chosenSite = sites[0].id;
            loadChartReadings(); // draw the chart
        } else {
            document.getElementById("error-message").textContent = "No monitoring sites are registered.";
            document.getElementById("error-message").style.display = "block";
        }
    } catch (error) {
        console.error("Site list failed to load: ", error);
        document.getElementById("error-message").textContent = "Could not load sites.";
        document.getElementById("error-message").style.display = "block";
    }
}

// -------------------------------------------------------------------------
// Fetch readings data
// called every time user changes an option from dropdown menu
// fetches readings then sends them to Chart.js
// -------------------------------------------------------------------------
async function loadChartReadings() {

    // Show the loading text and hide any previous error
    document.getElementById("loading-message").style.display = "block";
    document.getElementById("error-message").style.display   = "none";

    // CSV dataset runs from 2022-01-01 to 2023-12-31
    // we build the date string ourselves using formatLocalDate() below,
    // which always produces the exact date we specify with no timezone shifts
    const data_end_ms = new Date("2023-12-31T23:00:00").getTime();
    const from_ms     = data_end_ms - chosenDays * 24 * 60 * 60 * 1000;

    // formatLocalDate() takes a millisecond timestamp and returns a string like
    // "2023-12-25T10:30:00", the format Ktor's LocalDateTime.parse() expects
    // Date getFullYear/getMonth/etc. methods read local time,
    // so there is no UTC conversion and no risk of the date shifting by a day
    //
    // padStart allows single digits to get a starting 0,
    // e.g. month 3 becomes "03", this is needed for the database store
    function formatLocalDate(ms) {
        const d = new Date(ms);
        const yyyy = d.getFullYear();
        const mm   = String(d.getMonth() + 1).padStart(2, "0"); // getMonth() is 0-indexed, so +1
        const dd   = String(d.getDate()).padStart(2, "0");
        const hh   = String(d.getHours()).padStart(2, "0");
        const min  = String(d.getMinutes()).padStart(2, "0");
        const ss   = String(d.getSeconds()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}T${hh}:${min}:${ss}`;
    }

    const fromStr = formatLocalDate(from_ms);
    const toStr   = formatLocalDate(data_end_ms);
    const url = `/api/readings?site=${chosenSite}&from=${encodeURIComponent(fromStr)}&to=${encodeURIComponent(toStr)}`;

    try {
        const response = await fetch(url);

        // response.ok is true for HTTP 200-299, false for 400/500 errors
        if ( !response.ok ) {
            throw new Error("Server returned status "+response.status);
        }

        // parse JSON body into an array of ReadingDTO objects
        const readings = await response.json();

        // hides loading sign since graph is loaded
        document.getElementById("loading-message").style.display = "none";
        drawChart(readings);

    } catch (error) {
        // logging to console for debugging
        console.error("Chart failed to load data: ", error);
        document.getElementById("loading-message").style.display = "none";
        document.getElementById("error-message").style.display = "block";
    }
}

// -------------------------------------------------------------------------
// Drawing the chart
// array of ReadingDto objects rendered into a chart.js line graph
// called by loadChartReadings() once data arrives
// -------------------------------------------------------------------------
function drawChart(readings) {
    // .map() creates a new array by transforming each element
    let yAxisValues;
    let yAxisLabel;

    if ( chosenParam === "temp" ) {
        // ambientTemperatureC is field name from ReadingDTO in DataTransferObjects.kt
        yAxisValues = readings.map( function(r) { return r.ambientTemperatureC; });
        yAxisLabel  = "Ambient Temperature (°C)";
    } else {
        // accelMagG measures movement in g-force from accelerometer
        yAxisValues = readings.map(function(r) { return r.accelMagG; });
        yAxisLabel  = "Activity (accel_mag_g)";
    }

    // each reading has a timestamp
    // .slice(0, 10) takes the first 10 characters which gives just the date
    const xAxisLabels = readings.map( function(r) {
        return r.timeStamp.slice(0, 10);
    } );

    // create new instance of graph if one exists
    if ( chartJsInstance !== null ) {
        chartJsInstance.destroy();
        chartJsInstance = null;
    }

    // .getContext("2d") returns 2D drawing surface for Chart.js to draw onto
    const canvas = document.getElementById("trends-chart");
    const ctx = canvas.getContext("2d");

    // create the line chart in Chart.js
    chartJsInstance = new Chart(ctx, {
        type: "line",
        data: {
            labels: xAxisLabels,
            datasets: [{
                label:           yAxisLabel,
                data:            yAxisValues,
                borderColor:     "rgb(130, 60, 200)",
                backgroundColor: "rgba(130, 60, 200, 0.1)",
                borderWidth:     2,
                // pointRadius: 0 hides dots, with hundreds of readings they overlap
                pointRadius:     0,
                // tension: 0.3 adds a slight smooth curve (0 = straight, 1 = very wavy)
                tension:         0.3
            }]
        },

        options: {
            responsive:          true,
            // maintainAspectRatio: false lets us set the height via CSS
            maintainAspectRatio: false,

            plugins: {
                legend: { display: true, position: "top" },
                tooltip: {
                    // shows tooltip when hovering anywhere vertically, not just on a dot
                    mode:      "index",
                    intersect: false
                }
            },

            scales: {
                x: {
                    title: {
                        display: true,
                        text:    "Time (" + chosenDays + " days)"
                    },
                    ticks: {
                        // limits how many X axis labels are shown to avoid crowding
                        maxTicksLimit: 10
                    }
                },
                y: {
                    title: { display: true, text: yAxisLabel }
                }
            }
        }
    });
}

// -------------------------------------------------------------------------
// Event Listeners
// connect html elements to our JS functions to enable chart
// listens for whenever user makes a change
// -------------------------------------------------------------------------

// re-fetch when user selects different herd
document.getElementById("site-select").addEventListener("change",function(event) {
    chosenSite = event.target.value;
    loadChartReadings();
});

// time toggle (7 days / 30 days)
document.getElementById("time-toggle").addEventListener( "click",function(event){
    const click = event.target;
    // only respond if an actual button was clicked not div surrounding it
    if (click.tagName !== "BUTTON") return;

    // remove "chosen" from all buttons, then add it to the clicked one
    document.querySelectorAll("#time-toggle button").forEach(function(btn) {
        btn.classList.remove("selected");
    });
    click.classList.add("selected");

    // dataset.days reads the data-days="7" HTML attribute as a string
    // parseInt(..., 10) converts it to the number 7 (base 10)
    chosenDays = parseInt(click.dataset.days, 10);
    loadChartReadings();
});

// parameter toggle
document.getElementById("param-toggle").addEventListener("click", function(event) {
    const click = event.target;
    if (click.tagName !== "BUTTON") return;

    document.querySelectorAll("#param-toggle button").forEach(function(btn) {
        btn.classList.remove("selected");
    });
    click.classList.add("selected");

    // dataset.param reads the data-param="temp" or data-param="activity" attribute
    chosenParam = click.dataset.param;
    loadChartReadings();
});

// fetches site lis, fills dropdown and auto calls loadChartReadings()
loadSites();
