// cd app-telemetry
// npx wrangler deploy

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

    // ── 2. Endpoint: POST /api/rating (User 1-5 Star Rating Submission) ──
    if (url.pathname === "/api/rating" && request.method === "POST") {
      try {
        const body = await request.json().catch(() => ({}));
        const installId = body.install_id || "Anonymous";
        const stars = Math.min(5, Math.max(1, parseInt(body.stars || "5", 10)));
        const deviceModel = body.device_model || "Unknown Device";

        await env.DB.prepare(
          "INSERT INTO ratings (install_id, stars, device_model) VALUES (?, ?, ?)"
        ).bind(installId, stars, deviceModel).run();

        return new Response(JSON.stringify({ success: true, stars }), {
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

    // ── 3. Endpoint: POST /api/feedback (User Review Submission) ──
    if (url.pathname === "/api/feedback" && request.method === "POST") {
      try {
        const body = await request.json().catch(() => ({}));
        const installId = body.install_id || "Anonymous";
        const review = (body.review || "").trim();
        const deviceModel = body.device_model || "Unknown Device";

        if (review.length > 0) {
          await env.DB.prepare(
            "INSERT INTO feedbacks (install_id, review, device_model) VALUES (?, ?, ?)"
          ).bind(installId, review, deviceModel).run();
        }

        return new Response(JSON.stringify({ success: true }), {
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

    function getDynamicDownloadUrl(versionName, rawUrl) {
      if (!versionName) return rawUrl || "https://github.com/arvindminz/video-downloader-releases/releases/download/Downloader/VideoDownloader.v1.1.3.apk";
      const cleanVer = versionName.startsWith("v") ? versionName : "v" + versionName;
      if (rawUrl && rawUrl.includes("github.com") && rawUrl.includes("/releases/download/")) {
        const baseUrl = rawUrl.substring(0, rawUrl.lastIndexOf("/") + 1);
        return baseUrl + "VideoDownloader." + cleanVer + ".apk";
      }
      return `https://github.com/arvindminz/video-downloader-releases/releases/download/Downloader/VideoDownloader.${cleanVer}.apk`;
    }

    // ── 4. Endpoint: GET /api/check-update ──
    if (url.pathname === "/api/check-update") {
      try {
        const userVersionCode = parseInt(url.searchParams.get("version_code") || "1", 10);

        const latestRelease = await env.DB.prepare(
          "SELECT version_code, version_name, download_url, release_notes FROM app_updates ORDER BY id DESC LIMIT 1"
        ).first();

        const latestCode = latestRelease ? latestRelease.version_code : 5;
        const latestVersion = latestRelease ? latestRelease.version_name : "v1.1.3";
        const rawUrl = latestRelease ? latestRelease.download_url : "";
        const downloadUrl = getDynamicDownloadUrl(latestVersion, rawUrl);
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

    // ── 5. Endpoint: POST /api/publish-update ──
    if (url.pathname === "/api/publish-update" && request.method === "POST") {
      try {
        const formData = await request.formData();
        const versionCode = parseInt(formData.get("version_code") || "2", 10);
        const versionName = formData.get("version_name") || "v1.1.1";
        const downloadUrl = formData.get("download_url") || "https://github.com/arvindminz/video-downloader-releases/releases/download/Downloader/VideoDownloader.v1.1.1.apk";
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
              body { font-family: system-ui, sans-serif; background: #090d16; color: #f8fafc; padding: 2rem; max-width: 600px; margin: auto; text-align: center; }
              .card { background: #111827; padding: 2rem; border-radius: 16px; border: 1px solid rgba(255,255,255,0.08); box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5); }
              .btn { background: #0284c7; color: white; padding: 12px 24px; border-radius: 10px; text-decoration: none; display: inline-block; margin-top: 1rem; font-weight: bold; }
            </style>
          </head>
          <body>
            <div class="card">
              <h1 style="color: #34d399;">✅ App Update Published!</h1>
              <p><b>Version:</b> ${versionName} (Code: ${versionCode})</p>
              <p><b>Link:</b> ${downloadUrl}</p>
              <p>All user apps will now display the <b>🚀 New Update Available!</b> notice.</p>
              <a href="/admin" class="btn">⬅️ Back to Admin Panel</a>
              <a href="/stats" class="btn" style="background:#334155;">📊 View Dashboard</a>
            </div>
          </body>
          </html>
        `, { headers: { "Content-Type": "text/html" } });
      } catch (e) {
        return new Response("Failed to publish update: " + e.message, { status: 500 });
      }
    }

    // ── 6. Endpoint: GET /admin (Web Admin Release Portal) ──
    if (url.pathname === "/admin") {
      const currentRelease = await env.DB.prepare(
        "SELECT version_code, version_name, download_url, release_notes, created_at FROM app_updates ORDER BY id DESC LIMIT 1"
      ).first();

      const avgRating = await env.DB.prepare("SELECT AVG(stars) as avg, COUNT(*) as count FROM ratings").first();
      const recentFeedbacks = await env.DB.prepare("SELECT review, device_model, created_at FROM feedbacks ORDER BY id DESC LIMIT 100").all();

      const html = `
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <title>Publish App Update — Admin Panel</title>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #090d16; color: #f8fafc; padding: 2rem; max-width: 650px; margin: auto; }
            .card { background: #111827; padding: 1.8rem; border-radius: 16px; border: 1px solid rgba(255,255,255,0.08); box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5); margin-bottom: 1.5rem; }
            h1 { color: #38bdf8; margin-top: 0; font-size: 1.6rem; }
            h2 { color: #f43f5e; border-bottom: 1px solid #1f2937; padding-bottom: 0.5rem; font-size: 1.1rem; margin-top: 0; }
            label { display: block; font-weight: 600; margin-top: 1rem; margin-bottom: 0.3rem; color: #9ca3af; font-size: 0.9rem; }
            input, textarea { width: 100%; padding: 0.75rem; border-radius: 10px; border: 1px solid #1f2937; background: #030712; color: white; box-sizing: border-box; font-size: 0.95rem; }
            button { background: #f43f5e; color: white; border: none; padding: 0.8rem 1.5rem; border-radius: 10px; font-weight: bold; font-size: 1rem; cursor: pointer; width: 100%; margin-top: 1.5rem; }
            button:hover { background: #e11d48; }
            .current-info { background: #030712; padding: 1rem; border-radius: 10px; font-size: 0.9rem; border-left: 4px solid #38bdf8; }
            .nav-link { color: #38bdf8; text-decoration: none; font-size: 0.9rem; font-weight: bold; }
            .star-score { font-size: 2.2rem; font-weight: 800; color: #facc15; margin: 0.3rem 0; }
          </style>
        </head>
        <body>
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 1.5rem;">
            <h1>📱 Video Downloader Release Portal</h1>
            <a href="/stats" class="nav-link">📊 View Live Dashboard</a>
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

              <label>VERSION NAME (Display Version e.g. v1.1.3):</label>
              <input type="text" name="version_name" value="${currentRelease ? currentRelease.version_name : 'v1.1.3'}" required>

              <label>DOWNLOAD URL (GitHub Release Direct APK link):</label>
              <input type="url" name="download_url" value="${currentRelease ? currentRelease.download_url : 'https://github.com/arvindminz/video-downloader-releases/releases/download/Downloader/VideoDownloader.v1.1.3.apk'}" required>

              <label>RELEASE NOTES (What's new in this version):</label>
              <textarea name="release_notes" rows="3" required>${currentRelease ? currentRelease.release_notes : 'Added 4K playlist support, faster download speed, and UI improvements.'}</textarea>

              <button type="submit">🚀 Publish App Update</button>
            </form>
          </div>
        </body>
        </html>
      `;

      return new Response(html, { headers: { "Content-Type": "text/html" } });
    }

    // ── 7. Endpoint: GET /stats (Visual Tabbed Admin Dashboard) ──
    if (url.pathname === "/stats" || url.pathname === "/") {
      try {
        const totalCount = await env.DB.prepare("SELECT COUNT(*) as count FROM installs").first();
        const avgRating = await env.DB.prepare("SELECT AVG(stars) as avg, COUNT(*) as count FROM ratings").first();
        const recentFeedbacks = await env.DB.prepare("SELECT review, device_model, created_at FROM feedbacks ORDER BY id DESC LIMIT 100").all();

        const countryStats = await env.DB.prepare(
          "SELECT country, COUNT(*) as total FROM installs GROUP BY country ORDER BY total DESC LIMIT 30"
        ).all();
        const deviceStats = await env.DB.prepare(
          "SELECT COALESCE(device_model, 'Unknown Device') as device, COUNT(*) as total FROM installs GROUP BY device ORDER BY total DESC LIMIT 30"
        ).all();
        const recentInstalls = await env.DB.prepare(
          "SELECT install_id, country, city, COALESCE(device_model, 'Unknown Device') as device, created_at FROM installs ORDER BY id DESC LIMIT 30"
        ).all();

        const html = `
          <!DOCTYPE html>
          <html lang="en">
          <head>
            <meta charset="UTF-8">
            <title>Video Downloader — Admin Dashboard</title>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #090d16; color: #f8fafc; padding: 2rem; max-width: 1000px; margin: auto; }
              .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
              h1 { color: #38bdf8; margin: 0; font-size: 1.8rem; }
              .card { background: #111827; padding: 1.5rem; border-radius: 12px; margin-bottom: 1.5rem; border: 1px solid rgba(255,255,255,0.08); box-shadow: 0 4px 12px rgba(0,0,0,0.3); }
              h2 { color: #f43f5e; border-bottom: 1px solid #1f2937; padding-bottom: 0.5rem; font-size: 1.1rem; margin-top: 0; }

              /* Nav Buttons Row */
              .nav-buttons { display: flex; gap: 0.6rem; flex-wrap: wrap; margin-bottom: 1.5rem; }
              .tab-btn { background: #111827; color: #9ca3af; border: 1px solid rgba(255,255,255,0.08); padding: 0.65rem 1.2rem; border-radius: 8px; font-weight: 600; font-size: 0.9rem; cursor: pointer; transition: all 0.2s ease; text-decoration: none; display: inline-flex; align-items: center; }
              .tab-btn:hover { background: #1f2937; color: #f8fafc; }
              .tab-btn.active { background: #0284c7; color: white; border-color: #38bdf8; box-shadow: 0 2px 8px rgba(2, 132, 199, 0.4); }
              .release-btn { background: #f43f5e; color: white; border-color: #f43f5e; }
              .release-btn:hover { background: #e11d48; }

              /* Tab Sections */
              .tab-section { display: none; }
              .tab-section.active { display: block; }

              table { width: 100%; border-collapse: collapse; margin-top: 0.5rem; }
              th, td { text-align: left; padding: 0.65rem; border-bottom: 1px solid #1f2937; font-size: 0.92rem; }
              th { color: #9ca3af; font-weight: 600; }
              .stat-num { font-size: 3.5rem; font-weight: 800; color: #34d399; margin: 0.3rem 0; }
              .star-score { font-size: 3rem; font-weight: 800; color: #facc15; margin: 0.3rem 0; }
              .sub-text { color: #9ca3af; font-size: 0.85rem; }
              .badge { background: #0284c7; padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: bold; }
              .device-badge { background: #1f2937; color: #38bdf8; padding: 2px 8px; border-radius: 4px; font-size: 0.85rem; font-weight: 600; }
              .time-text { color: #facc15; font-weight: 500; font-size: 0.88rem; }
            </style>
          </head>
          <body>
            <div class="header">
              <h1>📱 Video Downloader Admin Dashboard</h1>
            </div>

            <!-- KPI Summary Cards -->
            <div style="display:grid; grid-template-columns: 1fr 1fr; gap: 1.5rem;">
              <div class="card">
                <h2>TOTAL UNIQUE INSTALLS</h2>
                <div class="stat-num">${totalCount ? totalCount.count : 0}</div>
                <div class="sub-text">Calculated from unique first-launch device pings</div>
              </div>

              <div class="card">
                <h2>USER STAR RATING AVERAGE</h2>
                <div class="star-score">⭐ ${(avgRating && avgRating.avg) ? avgRating.avg.toFixed(1) : '5.0'} / 5.0</div>
                <div class="sub-text">Total ${avgRating ? avgRating.count : 0} ratings submitted in-app</div>
              </div>
            </div>

            <!-- Navigation Buttons -->
            <div class="nav-buttons">
              <button class="tab-btn active" onclick="switchTab('reviews')">💬 USER REVIEWS & FEEDBACK</button>
              <button class="tab-btn" onclick="switchTab('devices')">📱 TOP DEVICE MODELS</button>
              <button class="tab-btn" onclick="switchTab('countries')">🌐 TOP COUNTRIES</button>
              <button class="tab-btn" onclick="switchTab('logs')">📋 RECENT INSTALL LOGS</button>
              <a href="/admin" class="tab-btn release-btn">🚀 RELEASE ADMIN PORTAL</a>
            </div>

            <!-- Tab 1: User Reviews & Feedback -->
            <div id="tab-reviews" class="tab-section active card">
              <h2>USER REVIEWS & FEEDBACK (${recentFeedbacks.results ? recentFeedbacks.results.length : 0})</h2>
              ${(recentFeedbacks.results || []).map(f => `
                <div style="background:#030712; padding:0.9rem; border-radius:8px; margin-bottom:0.7rem; border-left:4px solid #38bdf8;">
                  <p style="margin:0; font-size:0.95rem; color:#f8fafc;">"${f.review}"</p>
                  <div style="color:#9ca3af; font-size:0.8rem; margin-top:0.4rem;">📱 ${f.device_model} • <span style="color:#facc15;">${formatToIST(f.created_at)}</span></div>
                </div>
              `).join('') || '<p style="color:#9ca3af;">No feedback submitted yet</p>'}
            </div>

            <!-- Tab 2: Top Device Models -->
            <div id="tab-devices" class="tab-section card">
              <h2>TOP DEVICE MODELS</h2>
              <table>
                <thead><tr><th>Device Model</th><th>Installs</th></tr></thead>
                <tbody>
                  ${(deviceStats.results || []).map(r => `<tr><td><span class="device-badge">📱 ${r.device}</span></td><td><b>${r.total}</b></td></tr>`).join('') || '<tr><td colspan="2">No data yet</td></tr>'}
                </tbody>
              </table>
            </div>

            <!-- Tab 3: Top Countries -->
            <div id="tab-countries" class="tab-section card">
              <h2>TOP COUNTRIES</h2>
              <table>
                <thead><tr><th>Country</th><th>Installs</th></tr></thead>
                <tbody>
                  ${(countryStats.results || []).map(r => `<tr><td><span class="badge">${r.country}</span></td><td><b>${r.total}</b></td></tr>`).join('') || '<tr><td colspan="2">No data yet</td></tr>'}
                </tbody>
              </table>
            </div>

            <!-- Tab 4: Recent Install Logs -->
            <div id="tab-logs" class="tab-section card">
              <h2>RECENT INSTALL LOGS (IST TIME)</h2>
              <table>
                <thead><tr><th>Install ID</th><th>Device Model</th><th>Location</th><th>Timestamp (IST)</th></tr></thead>
                <tbody>
                  ${(recentInstalls.results || []).map(r => `<tr><td><code>${r.install_id.substring(0, 8)}...</code></td><td><span class="device-badge">${r.device}</span></td><td>${r.city}, ${r.country}</td><td><span class="time-text">${formatToIST(r.created_at)}</span></td></tr>`).join('') || '<tr><td colspan="4">No installs registered yet</td></tr>'}
                </tbody>
              </table>
            </div>

            <script>
              function switchTab(tabName) {
                document.querySelectorAll('.tab-section').forEach(sec => sec.classList.remove('active'));
                document.querySelectorAll('.tab-btn').forEach(btn => {
                  if (!btn.classList.contains('release-btn')) btn.classList.remove('active');
                });

                const activeSec = document.getElementById('tab-' + tabName);
                if (activeSec) activeSec.classList.add('active');

                const activeBtn = Array.from(document.querySelectorAll('.tab-btn')).find(btn => btn.getAttribute('onclick')?.includes(tabName));
                if (activeBtn) activeBtn.classList.add('active');
              }
            </script>
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
