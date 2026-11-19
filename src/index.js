export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    function formatToIST(utcString) {
      if (!utcString) return "Unknown";
      try {
        const utcDate = new Date(utcString.replace(" ", "T") + "Z");
        if (isNaN(utcDate.getTime())) return utcString;

        return utcDate.toLocaleString("en-IN", {
          timeZone: "Asia/Kolkata",
          day: "2-digit",
          month: "short",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
          hour12: true
        }) + " IST";
      } catch (_e) {
        return utcString;
      }
    }

    // ── 1. Endpoint: POST /api/install (App First Launch Ping) ──
    if (url.pathname === "/api/install" && request.method === "POST") {
      try {
        const body = await request.json().catch(() => ({}));
        const installId = body.install_id || crypto.randomUUID();
        const deviceModel = body.device_model || "Unknown Device";

        const country = request.cf?.country || "Unknown";
        const city = request.cf?.city || "Unknown";
        const region = request.cf?.region || "Unknown";

        await env.DB.prepare(
          "INSERT OR IGNORE INTO installs (install_id, country, city, region, device_model) VALUES (?, ?, ?, ?, ?)"
        ).bind(installId, country, city, region, deviceModel).run();

        await env.DB.prepare(
          "UPDATE installs SET device_model = ? WHERE install_id = ? AND (device_model IS NULL OR device_model = 'Unknown Device')"
        ).bind(deviceModel, installId).run();

        return new Response(JSON.stringify({ success: true, country, city, deviceModel }), {
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
          },
          status: 200,
        });
      } catch (e) {
        return new Response(JSON.stringify({ error: e.message }), {
          headers: { "Content-Type": "application/json" },
          status: 500,
        });
      }
    }

    // ── 2. Endpoint: GET /api/check-update (App Reads Dynamic Update Specs from D1) ──
    if (url.pathname === "/api/check-update") {
      try {
        const userVersionCode = parseInt(url.searchParams.get("version_code") || "1", 10);

        // Fetch latest published update from D1 database
        const latestRelease = await env.DB.prepare(
          "SELECT version_code, version_name, download_url, release_notes FROM app_updates ORDER BY id DESC LIMIT 1"
        ).first();

        const latestCode = latestRelease ? latestRelease.version_code : 1;
        const latestVersion = latestRelease ? latestRelease.version_name : "v1.0.0";
        const downloadUrl = latestRelease ? latestRelease.download_url : "https://drive.google.com/drive/folders/19GIJWEZPmPr-IO5W2CxugXzX8rkAjLyq?usp=sharing";
        const releaseNotes = latestRelease ? latestRelease.release_notes : "Regular maintenance update.";

        const updateData = {
          has_update: userVersionCode < latestCode,
          latest_version: latestVersion,
          version_code: latestCode,
          download_url: downloadUrl,
          release_notes: releaseNotes
        };

        return new Response(JSON.stringify(updateData), {
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
          },
          status: 200
        });
      } catch (e) {
        return new Response(JSON.stringify({ error: e.message }), { status: 500 });
      }
    }

    // ── 3. Endpoint: POST /api/publish-update (Admin Form Submission) ──
    if (url.pathname === "/api/publish-update" && request.method === "POST") {
      try {
        const formData = await request.formData();
        const versionCode = parseInt(formData.get("version_code") || "1", 10);
        const versionName = formData.get("version_name") || "v1.1.0";
        const downloadUrl = formData.get("download_url") || "https://drive.google.com/drive/folders/19GIJWEZPmPr-IO5W2CxugXzX8rkAjLyq?usp=sharing";
        const releaseNotes = formData.get("release_notes") || "New features and bug fixes.";

        await env.DB.prepare(
          "INSERT INTO app_updates (version_code, version_name, download_url, release_notes) VALUES (?, ?, ?, ?)"
        ).bind(versionCode, versionName, downloadUrl, releaseNotes).run();

        return new Response(`
          <!DOCTYPE html>
          <html>
          <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Update Published</title>
            <style>
              body { font-family: system-ui, sans-serif; background: #0f172a; color: #f8fafc; padding: 2rem; max-width: 600px; margin: auto; text-align: center; }
              .card { background: #1e293b; padding: 2rem; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.4); }
              .btn { background: #0284c7; color: white; padding: 10px 20px; border-radius: 8px; text-decoration: none; display: inline-block; margin-top: 1rem; font-weight: bold; }
            </style>
          </head>
          <body>
            <div class="card">
              <h1 style="color: #4ade80;">✅ App Update Published!</h1>
              <p><b>Version:</b> ${versionName} (Code: ${versionCode})</p>
              <p><b>Link:</b> ${downloadUrl}</p>
              <p>All user apps will now display the <b>🚀 New Update Available!</b> notice.</p>
              <a href="/admin" class="btn">⬅️ Back to Admin Panel</a>
              <a href="/stats" class="btn" style="background:#334155;">📊 View Stats</a>
            </div>
          </body>
          </html>
        `, { headers: { "Content-Type": "text/html" } });
      } catch (e) {
        return new Response("Failed to publish update: " + e.message, { status: 500 });
      }
    }

    // ── 4. Endpoint: GET /admin (Web Admin Release Portal) ──
    if (url.pathname === "/admin") {
      const currentRelease = await env.DB.prepare(
        "SELECT version_code, version_name, download_url, release_notes, created_at FROM app_updates ORDER BY id DESC LIMIT 1"
      ).first();

      const html = `
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <title>Publish App Update — Admin Panel</title>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 2rem; max-width: 650px; margin: auto; }
            .card { background: #1e293b; padding: 1.8rem; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.3); margin-bottom: 1.5rem; }
            h1 { color: #38bdf8; margin-top: 0; font-size: 1.6rem; }
            label { display: block; font-weight: 600; margin-top: 1rem; margin-bottom: 0.3rem; color: #94a3b8; font-size: 0.9rem; }
            input, textarea { width: 100%; padding: 0.75rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: white; box-sizing: border-box; font-size: 0.95rem; }
            button { background: #f43f5e; color: white; border: none; padding: 0.8rem 1.5rem; border-radius: 8px; font-weight: bold; font-size: 1rem; cursor: pointer; width: 100%; margin-top: 1.5rem; }
            button:hover { background: #e11d48; }
            .current-info { background: #0f172a; padding: 1rem; border-radius: 8px; font-size: 0.9rem; border-left: 4px solid #38bdf8; }
            .nav-link { color: #38bdf8; text-decoration: none; font-size: 0.9rem; font-weight: bold; }
          </style>
        </head>
        <body>
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 1rem;">
            <h1>📱 Video Downloader Release Portal</h1>
            <a href="/stats" class="nav-link">📊 View Stats</a>
          </div>

          <div class="card">
            <h2>Current Active Release</h2>
            <div class="current-info">
              <p><b>Version Name:</b> ${currentRelease ? currentRelease.version_name : 'v1.0.0'}</p>
              <p><b>Version Code:</b> ${currentRelease ? currentRelease.version_code : 1}</p>
              <p><b>Download URL:</b> <a href="${currentRelease ? currentRelease.download_url : '#'}" target="_blank" style="color:#38bdf8;">${currentRelease ? currentRelease.download_url : 'None'}</a></p>
              <p><b>Notes:</b> ${currentRelease ? currentRelease.release_notes : 'Initial Release'}</p>
            </div>
          </div>

          <div class="card">
            <h2>Publish New App Update</h2>
            <form action="/api/publish-update" method="POST">
              <label>NEW VERSION CODE (Must match versionCode in build.gradle.kts):</label>
              <input type="number" name="version_code" value="${(currentRelease ? currentRelease.version_code : 1) + 1}" required>

              <label>VERSION NAME (Display Version e.g. v1.1.0):</label>
              <input type="text" name="version_name" value="v1.1.0" required>

              <label>DOWNLOAD URL (Google Drive / Direct APK link):</label>
              <input type="url" name="download_url" value="https://drive.google.com/drive/folders/19GIJWEZPmPr-IO5W2CxugXzX8rkAjLyq?usp=sharing" required>

              <label>RELEASE NOTES (What's new in this version):</label>
              <textarea name="release_notes" rows="3" required>Added 4K playlist support, faster download speed, and UI improvements.</textarea>

              <button type="submit">🚀 Publish App Update</button>
            </form>
          </div>
        </body>
        </html>
      `;

      return new Response(html, { headers: { "Content-Type": "text/html" } });
    }

    // CORS preflight handling
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type"
        }
      });
    }

    // ── 5. Endpoint: GET /stats (Visual Web Dashboard) ──
    if (url.pathname === "/stats" || url.pathname === "/") {
      try {
        const totalCount = await env.DB.prepare("SELECT COUNT(*) as count FROM installs").first();
        const countryStats = await env.DB.prepare(
          "SELECT country, COUNT(*) as total FROM installs GROUP BY country ORDER BY total DESC LIMIT 20"
        ).all();
        const cityStats = await env.DB.prepare(
          "SELECT city, country, COUNT(*) as total FROM installs GROUP BY city ORDER BY total DESC LIMIT 20"
        ).all();
        const deviceStats = await env.DB.prepare(
          "SELECT COALESCE(device_model, 'Unknown Device') as device, COUNT(*) as total FROM installs GROUP BY device ORDER BY total DESC LIMIT 20"
        ).all();
        const recentInstalls = await env.DB.prepare(
          "SELECT install_id, country, city, COALESCE(device_model, 'Unknown Device') as device, created_at FROM installs ORDER BY id DESC LIMIT 20"
        ).all();

        const html = `
          <!DOCTYPE html>
          <html lang="en">
          <head>
            <meta charset="UTF-8">
            <title>Video Downloader — Telemetry Dashboard</title>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 2rem; max-width: 950px; margin: auto; }
              .card { background: #1e293b; padding: 1.5rem; border-radius: 12px; margin-bottom: 1.5rem; box-shadow: 0 4px 12px rgba(0,0,0,0.3); }
              h1 { color: #38bdf8; margin-top: 0; font-size: 1.8rem; }
              h2 { color: #f43f5e; border-bottom: 1px solid #334155; padding-bottom: 0.5rem; font-size: 1.1rem; margin-top: 0; }
              table { width: 100%; border-collapse: collapse; margin-top: 0.5rem; }
              th, td { text-align: left; padding: 0.6rem; border-bottom: 1px solid #334155; font-size: 0.92rem; }
              th { color: #94a3b8; font-weight: 600; }
              .stat-num { font-size: 3.8rem; font-weight: 800; color: #4ade80; margin: 0.5rem 0; }
              .sub-text { color: #94a3b8; font-size: 0.85rem; }
              .badge { background: #0284c7; padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: bold; }
              .device-badge { background: #334155; color: #38bdf8; padding: 2px 8px; border-radius: 4px; font-size: 0.85rem; font-weight: 600; }
              .time-text { color: #facc15; font-weight: 500; font-size: 0.88rem; }
              .admin-link { float: right; color: #f43f5e; text-decoration: none; font-size: 0.95rem; font-weight: bold; }
            </style>
          </head>
          <body>
            <a href="/admin" class="admin-link">🚀 Release Admin Portal</a>
            <h1>📱 Video Downloader Analytics</h1>
            <div class="card">
              <h2>TOTAL UNIQUE INSTALLS</h2>
              <div class="stat-num">${totalCount ? totalCount.count : 0}</div>
              <div class="sub-text">Calculated from unique first-launch device pings</div>
            </div>

            <div class="card">
              <h2>TOP DEVICE MODELS</h2>
              <table>
                <thead><tr><th>Device Model</th><th>Installs</th></tr></thead>
                <tbody>
                  ${(deviceStats.results || []).map(r => `<tr><td><span class="device-badge">📱 ${r.device}</span></td><td><b>${r.total}</b></td></tr>`).join('') || '<tr><td colspan="2">No data yet</td></tr>'}
                </tbody>
              </table>
            </div>

            <div class="card">
              <h2>TOP COUNTRIES</h2>
              <table>
                <thead><tr><th>Country</th><th>Installs</th></tr></thead>
                <tbody>
                  ${(countryStats.results || []).map(r => `<tr><td><span class="badge">${r.country}</span></td><td><b>${r.total}</b></td></tr>`).join('') || '<tr><td colspan="2">No data yet</td></tr>'}
                </tbody>
              </table>
            </div>

            <div class="card">
              <h2>TOP CITIES</h2>
              <table>
                <thead><tr><th>City</th><th>Country</th><th>Installs</th></tr></thead>
                <tbody>
                  ${(cityStats.results || []).map(r => `<tr><td>${r.city}</td><td>${r.country}</td><td><b>${r.total}</b></td></tr>`).join('') || '<tr><td colspan="3">No data yet</td></tr>'}
                </tbody>
              </table>
            </div>

            <div class="card">
              <h2>RECENT INSTALL LOGS (IST TIME)</h2>
              <table>
                <thead><tr><th>Install ID</th><th>Device Model</th><th>Location</th><th>Timestamp (IST)</th></tr></thead>
                <tbody>
                  ${(recentInstalls.results || []).map(r => `<tr><td><code>${r.install_id.substring(0, 8)}...</code></td><td><span class="device-badge">${r.device}</span></td><td>${r.city}, ${r.country}</td><td><span class="time-text">${formatToIST(r.created_at)}</span></td></tr>`).join('') || '<tr><td colspan="4">No installs registered yet</td></tr>'}
                </tbody>
              </table>
            </div>
          </body>
          </html>
        `;

        return new Response(html, { headers: { "Content-Type": "text/html" } });
      } catch (e) {
        return new Response("Error generating dashboard: " + e.message, { status: 500 });
      }
    }

    return new Response("Not Found", { status: 404 });
  }
};
