package com.cue.daymark;

/**
 * Host-side tests for the ABP cosmetic-filter converter: the import path
 * that turns untrusted filter-list text into CSS injected into WebView.
 */
public final class CosmeticFilterToCssSmoke {
    private static int assertions;

    private CosmeticFilterToCssSmoke() { }

    public static void main(String[] args) {
        cosmeticRulesBecomeOneDisplayNoneRule();
        networkCommentsAndExtendedRulesAreIgnored();
        oversizedAndMalformedSelectorsAreDropped();
        selectorCountIsCappedAtFiveHundred();
        emptyInputsProduceEmptyCss();
        networkOnlyListsCannotBeImportedAsPacks();
        filterListsImportAsCosmeticPacks();
        System.out.println("PASS cosmetic filter smoke tests: " + assertions + " assertions");
    }

    private static void cosmeticRulesBecomeOneDisplayNoneRule() {
        String css = CosmeticFilterToCss.toCss("example.com##.ad
##.promo
");
        check(".ad,.promo{display:none!important;}
".equals(css),
                "cosmetic ## rules compile into a single display:none rule");
    }

    private static void networkCommentsAndExtendedRulesAreIgnored() {
        String css = CosmeticFilterToCss.toCss(
                "! comment
"
                + "[Adblock Plus 2.0]
"
                + "||ads.example.com^
"
                + "-ad-banner.
"
                + "example.com#@#.sponsor
"
                + "example.com#?#div:has(.ad)
"
                + "example.com#$#div { color: red }
"
                + "example.com##.keep
");
        check(".keep{display:none!important;}
".equals(css),
                "network, comment, header and extended rules never leak into CSS");
    }

    private static void oversizedAndMalformedSelectorsAreDropped() {
        StringBuilder longSelector = new StringBuilder();
        for (int i = 0; i < 301; i++) longSelector.append('a');
        String css = CosmeticFilterToCss.toCss(
                "##" + longSelector + "
"
                + "##.ok{color:red}
"
                + "##.good
");
        check(".good{display:none!important;}
".equals(css),
                "selectors over 300 chars and selectors with braces are dropped");
    }

    private static void selectorCountIsCappedAtFiveHundred() {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < 600; i++) list.append("##.s").append(i).append('
');
        String css = CosmeticFilterToCss.toCss(list.toString());
        check(css.contains(".s499,"), "the cap keeps exactly the first 500 selectors");
        check(!css.contains(".s500"), "selectors past the 500 cap are dropped");
        int commas = 0;
        for (int i = 0; i < css.length(); i++) {
            if (css.charAt(i) == ',') commas++;
        }
        check(commas == 499, "the compiled rule has exactly 500 selectors");
    }

    private static void emptyInputsProduceEmptyCss() {
        check("".equals(CosmeticFilterToCss.toCss(null)), "null lists produce empty CSS");
        check("".equals(CosmeticFilterToCss.toCss("")), "empty lists produce empty CSS");
        check("".equals(CosmeticFilterToCss.toCss("  
 
")), "whitespace-only lists produce empty CSS");
        check("".equals(CosmeticFilterToCss.toCss("||ads.example.com^
-banner.
")),
                "lists without any ## rule produce empty CSS");
    }

    private static void networkOnlyListsCannotBeImportedAsPacks() {
        try {
            CosmeticFilterToCss.packFromFilterList("Net only", "||ads.example.com^
-banner.
");
            throw new AssertionError("network-only lists must not import");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("network-only"),
                    "the rejection explains that network-only lists cannot run in WebView");
        }
    }

    private static void filterListsImportAsCosmeticPacks() {
        BrowserExtension pack = CosmeticFilterToCss.packFromFilterList("My List!",
                "example.com##.ad
##.banner
");
        check(pack.id.startsWith("import.cosmetic."), "cosmetic packs use the import.cosmetic id prefix");
        check("My List!".equals(pack.name), "the requested pack name is kept");
        check(pack.matches.contains("*://*/*"), "cosmetic packs run on all pages");
        check(pack.css.contains("display:none!important"), "the pack css hides the imported selectors");
        check("".equals(pack.js), "cosmetic packs never ship JavaScript");
        check(pack.description.contains("Network blocking is not available"),
                "the pack description is honest about WebView limitations");

        BrowserExtension safeId = CosmeticFilterToCss.packFromFilterList(null, "##.ad
");
        check(safeId.id.startsWith("import.cosmetic."), "null names still get a safe id");
        check(!safeId.name.isEmpty(), "null names fall back to a default name");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
