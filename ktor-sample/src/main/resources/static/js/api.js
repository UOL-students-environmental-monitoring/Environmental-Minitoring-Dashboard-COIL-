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

export async function getAlerts() {
    return requestJson("/api/alerts");
}

export async function getReadings(siteId) {
    const params = new URLSearchParams({ site: siteId });
    return requestJson(`/api/readings?${params.toString()}`);
}

export async function getDashboardData() {
    const sites = await getSites();
    // A missing reading for one herd should not stop the whole dashboard from loading.
    const readingResults = await Promise.allSettled(
        sites.map(async (site) => ({
            site,
            readings: await getReadings(site.id),
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
        alerts: await getAlerts(),
    };
}
