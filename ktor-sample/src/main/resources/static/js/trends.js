let chosenSite = "";
let chosenDays = 7;
let chosenParam = "temp";
let chartJsInstance = null;

async function loadSites() {
    try {
        const response = await fetch("/api/sites");
        if (!response.ok) {
            throw new Error("Server returned status " + response.status);
        }

        const sites = await response.json();
        const choice = document.getElementById("site-select");
        choice.innerHTML = "";

        sites.forEach(function(site) {
            const option = document.createElement("option");
            option.value = site.id;
            option.textContent = site.id;
            choice.appendChild(option);
        });

        if (sites.length > 0) {
            chosenSite = sites[0].id;
            loadChartReadings();
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

async function loadChartReadings() {
    document.getElementById("loading-message").style.display = "block";
    document.getElementById("error-message").style.display = "none";

    // Match the bundled CSV range so the initial chart has data immediately.
    const dataEndMs = new Date("2023-12-31T23:00:00").getTime();
    const fromMs = dataEndMs - chosenDays * 24 * 60 * 60 * 1000;

    // Ktor parses LocalDateTime strings without timezone offsets.
    function formatLocalDate(ms) {
        const d = new Date(ms);
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        const hh = String(d.getHours()).padStart(2, "0");
        const min = String(d.getMinutes()).padStart(2, "0");
        const ss = String(d.getSeconds()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}T${hh}:${min}:${ss}`;
    }

    const fromStr = formatLocalDate(fromMs);
    const toStr = formatLocalDate(dataEndMs);
    const url = `/api/readings?site=${chosenSite}&from=${encodeURIComponent(fromStr)}&to=${encodeURIComponent(toStr)}`;

    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error("Server returned status " + response.status);
        }

        const readings = await response.json();
        document.getElementById("loading-message").style.display = "none";
        drawChart(readings);
    } catch (error) {
        console.error("Chart failed to load data: ", error);
        document.getElementById("loading-message").style.display = "none";
        document.getElementById("error-message").style.display = "block";
    }
}

function drawChart(readings) {
    let yAxisValues;
    let yAxisLabel;

    if (chosenParam === "temp") {
        yAxisValues = readings.map(function(r) { return r.ambientTemperatureC; });
        yAxisLabel = "Ambient Temperature (C)";
    } else {
        yAxisValues = readings.map(function(r) { return r.accelMagG; });
        yAxisLabel = "Activity (accel_mag_g)";
    }

    const xAxisLabels = readings.map(function(r) {
        return r.timeStamp.slice(0, 10);
    });

    if (chartJsInstance !== null) {
        chartJsInstance.destroy();
        chartJsInstance = null;
    }

    const canvas = document.getElementById("trends-chart");
    const ctx = canvas.getContext("2d");

    chartJsInstance = new Chart(ctx, {
        type: "line",
        data: {
            labels: xAxisLabels,
            datasets: [{
                label: yAxisLabel,
                data: yAxisValues,
                borderColor: "rgb(130, 60, 200)",
                backgroundColor: "rgba(130, 60, 200, 0.1)",
                borderWidth: 2,
                pointRadius: 0,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: true, position: "top" },
                tooltip: {
                    mode: "index",
                    intersect: false
                }
            },
            scales: {
                x: {
                    title: {
                        display: true,
                        text: "Time (" + chosenDays + " days)"
                    },
                    ticks: {
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

document.getElementById("site-select").addEventListener("change", function(event) {
    chosenSite = event.target.value;
    loadChartReadings();
});

document.getElementById("time-toggle").addEventListener("click", function(event) {
    const click = event.target;
    if (click.tagName !== "BUTTON") return;

    document.querySelectorAll("#time-toggle button").forEach(function(btn) {
        btn.classList.remove("selected");
    });
    click.classList.add("selected");

    chosenDays = parseInt(click.dataset.days, 10);
    loadChartReadings();
});

document.getElementById("param-toggle").addEventListener("click", function(event) {
    const click = event.target;
    if (click.tagName !== "BUTTON") return;

    document.querySelectorAll("#param-toggle button").forEach(function(btn) {
        btn.classList.remove("selected");
    });
    click.classList.add("selected");

    chosenParam = click.dataset.param;
    loadChartReadings();
});

loadSites();
