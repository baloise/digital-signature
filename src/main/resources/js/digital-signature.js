function bindCollapse(ul, limit, showMore) {
    if (limit < 0) {
        return;
    }

    let $ul = AJS.$(ul);

    hideElements($ul, limit);
    $ul.prepend("<li><a href='javascript:void(0);'>" + showMore + "</a></li>")
       .bind("click", function() {
           showAllElements(this);
       });
}

function hideElements($ul, limit) {
    const signedList = $ul.find("li.signeelist-signed");
    const missingList = $ul.find("li.signeelist-missing");

    let remainingCount = limit;

    if (signedList.length > 0) {
        let shownSignees = Math.min(signedList.length, Math.ceil(remainingCount / 2));
        remainingCount = remainingCount - shownSignees;
        for (let i = 0; i < signedList.length - shownSignees; i++) {
            AJS.$(signedList[i]).hide();
        }
    }

    for (let i = 0; i < missingList.length - remainingCount; i++) {
        AJS.$(missingList[i]).hide();
    }
}

function showAllElements(button) {
    let $button = AJS.$(button);
    $button.closest("ul").find("li").show();
    $button.remove();
}
