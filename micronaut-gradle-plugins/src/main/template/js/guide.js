var tocId = "table-of-content";
var mainId = "main";
var activeSectionId = "";
var activeSidebarLink;
var scrollSpyFrame = 0;

function addJsClass() {
    if (!document.body) {
        return;
    }
    if (!document.body.classList.contains("js")) {
        document.body.classList.add("js");
    }
    if (document.documentElement.classList.contains("dark-mode")) {
        document.body.classList.add("dark-mode");
    }
}

function preferredTheme() {
    var theme;
    if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
        theme = "dark";
    } else {
        theme = "light";
    }
    return theme;
}

function applyTheme(theme, persist) {
    var isDark = theme === "dark";
    document.documentElement.classList.toggle("dark-mode", isDark);
    if (document.body) {
        document.body.classList.toggle("dark-mode", isDark);
    }

    var switcher = document.getElementById("theme-switcher");
    if (switcher) {
        switcher.title = isDark ? "Switch to light theme" : "Switch to dark theme";
        switcher.setAttribute("aria-label", switcher.title);
        switcher.setAttribute("aria-pressed", String(isDark));
    }

}

function switchTheme(persist) {
    var isDark = document.documentElement.classList.contains("dark-mode") ||
        (document.body && document.body.classList.contains("dark-mode"));
    applyTheme(isDark ? "light" : "dark", persist);
}

function loadTheme() {
    applyTheme(preferredTheme(), false);
}

function updateSidebarButtons() {
    var collapsed = document.body && document.body.classList.contains("sidebar-collapsed");
    var opened = document.body && document.body.classList.contains("sidebar-open");
    var collapseButtons = document.getElementsByClassName("sidebar-collapse");
    var toggleButtons = document.getElementsByClassName("sidebar-toggle");

    for (var i = 0; i < collapseButtons.length; i++) {
        collapseButtons[i].setAttribute("aria-expanded", String(!collapsed));
        collapseButtons[i].title = collapsed ? "Expand sidebar" : "Collapse sidebar";
        collapseButtons[i].setAttribute("aria-label", collapseButtons[i].title);
    }

    for (var j = 0; j < toggleButtons.length; j++) {
        toggleButtons[j].setAttribute("aria-expanded", String(opened));
        toggleButtons[j].title = opened ? "Close sidebar" : "Open sidebar";
        toggleButtons[j].setAttribute("aria-label", toggleButtons[j].title);
    }
}

function hideTableOfContents() {
    if (document.body) {
        document.body.classList.add("sidebar-collapsed");
        document.body.classList.remove("sidebar-open");
    }
    updateSidebarButtons();
    goToLocation();
}

function showTableOfContents() {
    if (document.body) {
        document.body.classList.remove("sidebar-collapsed");
    }
    updateSidebarButtons();
    goToLocation();
}

function toggleTableOfContents() {
    if (!document.body) {
        return;
    }
    document.body.classList.toggle("sidebar-collapsed");
    document.body.classList.remove("sidebar-open");
    updateSidebarButtons();
}

function toggleSidebar() {
    if (!document.body) {
        return;
    }
    document.body.classList.toggle("sidebar-open");
    document.body.classList.remove("sidebar-collapsed");
    updateSidebarButtons();
}

function closeSidebar() {
    if (document.body) {
        document.body.classList.remove("sidebar-open");
    }
    updateSidebarButtons();
}

function goToLocation() {
    if (location.hash !== "") {
        history.replaceState(null, "", location.href);
    }
}

function scrollToTop() {
    if (document.body) {
        document.body.classList.add("sidebar-open");
    }
    updateSidebarButtons();
    document.body.scrollTop = 0;
    document.documentElement.scrollTop = 0;
}

function highlightMenu() {
    var sectionId;
    if (location.hash !== "") {
        sectionId = decodeURIComponent(location.hash.replace("#", ""));
    } else {
        sectionId = activeSectionFromScroll();
    }
    refreshActiveSidebarLink(sectionId, true);
}

function sidebarLinks() {
    return document.querySelectorAll("#" + tocId + " a[data-section]");
}

function findSidebarLink(sectionId) {
    if (!sectionId) {
        return null;
    }
    var links = sidebarLinks();
    for (var i = 0; i < links.length; i++) {
        if (links[i].getAttribute("data-section") === sectionId) {
            return links[i];
        }
    }
    return null;
}

function firstSidebarSectionId() {
    var links = sidebarLinks();
    return links.length ? links[0].getAttribute("data-section") : "";
}

function activeSectionFromScroll() {
    var sidebarSectionIds = {};
    var links = sidebarLinks();
    for (var i = 0; i < links.length; i++) {
        sidebarSectionIds[links[i].getAttribute("data-section")] = true;
    }

    var headings = document.querySelectorAll("#" + mainId + " h1[id], #" + mainId + " h2[id], #" + mainId + " h3[id], #" + mainId + " h4[id], #" + mainId + " h5[id], #" + mainId + " h6[id]");
    var topbar = document.getElementById("navigation");
    var activationTop = (topbar ? topbar.getBoundingClientRect().height : 0) + 24;
    var sectionId = firstSidebarSectionId();

    for (var j = 0; j < headings.length; j++) {
        if (!sidebarSectionIds[headings[j].id]) {
            continue;
        }
        if (headings[j].getBoundingClientRect().top <= activationTop) {
            sectionId = headings[j].id;
        } else {
            break;
        }
    }
    return sectionId;
}

function refreshActiveSidebarLink(sectionId, scrollLink) {
    var cssClass = "toc-item-highlighted";
    var activeClass = "active";
    var highlighted = document.getElementsByClassName(cssClass);
    while (highlighted.length) {
        highlighted[0].classList.remove(cssClass);
    }

    var activeLinks = document.querySelectorAll("#" + tocId + " a." + activeClass);
    for (var i = 0; i < activeLinks.length; i++) {
        activeLinks[i].classList.remove(activeClass);
    }

    activeSidebarLink = null;
    activeSectionId = "";
    if (!sectionId) {
        return;
    }

    var tocItem = document.getElementById("toc-item-" + sectionId);
    var link = tocItem && tocItem.tagName && tocItem.tagName.toLowerCase() === "a" ? tocItem : null;
    if (!link && tocItem) {
        link = tocItem.getElementsByTagName("a")[0];
    }
    if (!link) {
        link = findSidebarLink(sectionId);
    }
    if (link) {
        var parentSection = link.closest ? link.closest(".toc-section") : null;
        while (parentSection) {
            parentSection.open = true;
            parentSection = parentSection.parentElement && parentSection.parentElement.closest ? parentSection.parentElement.closest(".toc-section") : null;
        }
        link.classList.add(cssClass);
        link.classList.add(activeClass);
        activeSidebarLink = link;
        activeSectionId = sectionId;
        if (scrollLink) {
            link.scrollIntoView({ block: "nearest" });
        }
    }
}

function queueScrollSpy() {
    if (scrollSpyFrame) {
        return;
    }
    scrollSpyFrame = window.requestAnimationFrame(function () {
        var nextSectionId;
        scrollSpyFrame = 0;
        nextSectionId = activeSectionFromScroll();
        if (nextSectionId && nextSectionId !== activeSectionId) {
            refreshActiveSidebarLink(nextSectionId, true);
        }
    });
}

function fallbackCopyText(element) {
    var range;
    var selection;

    if (document.body.createTextRange) {
        range = document.body.createTextRange();
        range.moveToElementText(element);
        range.select();
    } else if (window.getSelection) {
        selection = window.getSelection();
        range = document.createRange();
        range.selectNodeContents(element);
        selection.removeAllRanges();
        selection.addRange(range);
    }

    try {
        document.execCommand("copy");
    } catch (err) {
        return false;
    }
    return true;
}

function copyToClipboard(el) {
    var source = el && el.parentNode ? el.parentNode.previousElementSibling : null;
    if (!source) {
        return;
    }

    var text = source.innerText || source.textContent || "";
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () {
            el.innerText = "Copied";
            window.setTimeout(function () {
                el.innerText = "Copy";
            }, 1400);
        }, function () {
            fallbackCopyText(source);
        });
    } else {
        fallbackCopyText(source);
    }
}

function initSidebarInteractions() {
    document.addEventListener("click", function (event) {
        if (!document.body || !document.body.classList.contains("sidebar-open")) {
            return;
        }
        var sidebar = document.getElementById(tocId);
        var topbar = document.getElementById("navigation");
        if (sidebar && sidebar.contains(event.target)) {
            return;
        }
        if (topbar && topbar.contains(event.target)) {
            return;
        }
        closeSidebar();
    });

    var sidebarLinks = document.querySelectorAll("#" + tocId + " a");
    for (var i = 0; i < sidebarLinks.length; i++) {
        sidebarLinks[i].addEventListener("click", function () {
            closeSidebar();
        });
    }
}

function initGuide() {
    addJsClass();
    loadTheme();
    updateSidebarButtons();
    highlightMenu();
    initSidebarInteractions();
    window.addEventListener("scroll", queueScrollSpy, { passive: true });
    window.addEventListener("resize", queueScrollSpy);
}

loadTheme();

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initGuide);
} else {
    initGuide();
}
