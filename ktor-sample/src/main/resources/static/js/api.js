const jsonHeaders = {
    Accept: "application/json",
};

async function requestJson(path) {
    const response = await fetch(path, { headers: jsonHeaders });

    if (!response.ok) {
        let message = `Request failed with status ${response.status}`;
        try {
            const body = await response.json();
            if (body.error) {
                message = body.error;
            }
        } catch (_error) {
            message = response.statusText || message;
        }
        throw new Error(message);
    }

    return response.json();
}

export async function getSites() {
    return requestJson("/api/sites");
}

export async function getAlerts(options = {}) {
    const params = new URLSearchParams(options);
    const suffix = params.size ? `?${params.toString()}` : "";
    return requestJson(`/api/alerts${suffix}`);
}

export async function getReadings(siteId, options = {}) {
    const params = new URLSearchParams({ site: siteId, ...options });
    return requestJson(`/api/readings?${params.toString()}`);
}

export async function getDashboardCriticalAlerts() {
    return requestJson("/api/dashboard/critical-alerts");
}

export async function getDashboardData() {
    const sites = await getSites();
    const recentWindow = {
        from: "2023-12-24T00:00:00",
        to: "2023-12-31T23:00:00",
    };
    // If one site has a bad row, the rest of the dashboard should still load.
    const readingResults = await Promise.allSettled(
        sites.map(async (site) => ({
            site,
            readings: await getReadings(site.id, recentWindow),
        })),
    );

    const siteReadings = readingResults.map((result) => {
        if (result.status === "fulfilled") {
            return result.value;
        }
        return {
            site: null,
            readings: [],
            error: result.reason.message,
        };
    });

    return {
        sites,
        siteReadings,
        alerts: await getDashboardCriticalAlerts(),
    };
}
