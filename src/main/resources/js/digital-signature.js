function bindCollapse(ul, limit, showMore) {
    if (limit < 0) {
        return;
    }

    let $ul = AJS.$(ul);

    if (hideElements($ul, limit)) {
        $ul.after("<a href='javascript:void(0);' class='show-all'>" + showMore + "</a>");
        $ul.siblings("a.show-all").bind("click", function () {
            showAllElements($ul);
        });
    }
}

function hideElements($ul, limit) {
    const signedList = $ul.find("li.signeelist-signed");
    const missingList = $ul.find("li.signeelist-missing");

    let remainingCount = limit;

    let isSomethingHidden = false;
    if (signedList.length > 0) {
        let shownSignees = Math.min(signedList.length, Math.ceil(remainingCount / 2));
        remainingCount = remainingCount - shownSignees;
        for (let i = 0; i < signedList.length - shownSignees; i++) {
            AJS.$(signedList[i]).hide()
            isSomethingHidden = true;
        }
    }

    for (let i = 0; i < missingList.length - remainingCount; i++) {
        AJS.$(missingList[i]).hide();
        isSomethingHidden = true;
    }

    return isSomethingHidden;
}

function showAllElements($ul) {
    $ul.find("li").show();
    $ul.siblings("a.show-all").remove();
}
