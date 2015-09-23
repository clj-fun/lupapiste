*** Settings ***

Documentation  Authority uses default filter
Suite teardown  Run Keywords  Logout  Apply minimal fixture now
Resource        ../../common_resource.robot
Suite setup     Apply minimal fixture now

*** Test Cases ***

Sonja logs in and creates an application
  Sonja logs in
  # FIXME: There is a problem with white space characters in suite variables (Sonja -> Sibbo Sonja, käsittelijää -> Ei käsittelijää)
  Set Suite Variable  ${sonja name}  Sonja
  Set Suite Variable  ${all handlers}  Kaikki
  Set Suite Variable  ${no authority}  käsittelijää
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  notice${secs}
  Set Suite Variable  ${propertyId}  753-423-2-41
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Sonja opens search page
  Go to page  applications
  Click by test id  toggle-advanced-filters
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  0
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Päivitetty  desc
  Element should be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]

Sonja selects application sorting
  Click sorting field  Tyyppi
  Sorting Should Be Set As  Tyyppi  desc
  Click sorting field  Tyyppi
  Sorting Should Be Set As  Tyyppi  asc

...selects handler
  Select From Autocomplete  div[@data-test-id="handlers-filter-component"]  ${sonja name}

...adds item into areas filter
  Select From Autocomplete  div[@data-test-id="areas-filter-component"]  Keskusta

...adds item into organizations filter
  Select From Autocomplete  div[@data-test-id="organization-filter-component"]  Sipoon yleisten alueiden rakentaminen

...adds item into operations filter
  Select From Autocomplete  div[@data-test-id="operations-filter-component"]  Asuinkerrostalon tai rivitalon rakentaminen

...adds item into tags filter
  Select From Autocomplete  div[@data-test-id="tags-filter-component"]  ylämaa

...saves MEGA filter
  Save advanced filter  MEGA

Sonja reloads the page and expects that saved filter is applied as default
  Reload Page
  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(@class,"autocomplete-selection")]//span[contains(text(), "MEGA")]

...filter setup should be shown as default
  Filter should contain tag  handler  ${sonja name}
  Filter should contain tag  areas  Keskusta
  Filter should contain tag  tags  ylämaa
  Filter should contain tag  operations  Asuinkerrostalon tai rivitalon rakentaminen
  Filter should contain tag  organization  Sipoon yleisten alueiden rakentaminen
  Filter item should contain X number of tags  handler  1
  Filter item should contain X number of tags  areas  1
  Filter item should contain X number of tags  tags  1
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  1
  Element should not be visible  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]

Sonja removes all but operations filter
  Select From Autocomplete  div[@data-test-id="handlers-filter-component"]  ${all handlers}
  Wait until  Click Element  xpath=//div[@data-test-id="areas-filter-component"]//ul[@class="tags"]//li[@class="tag"]//i
  Wait until  Click Element  xpath=//div[@data-test-id="tags-filter-component"]//ul[@class="tags"]//li[@class="tag"]//i
  Wait until  Click Element  xpath=//div[@data-test-id="organization-filter-component"]//ul[@class="tags"]//li[@class="tag"]//i
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Tyyppi  asc

Sonja closes and opens advanced filters
  Click by test id  toggle-advanced-filters
  Click by test id  toggle-advanced-filters
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Tyyppi  asc

Sonja opens an application and returns to application page
  Wait Until  Click Element  xpath=//table[@id="applications-list"]/tbody//tr[@data-test-address="${appname}"]
  Go to page  applications

Filter should be set as before visiting application
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  handler  0
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Tyyppi  asc

Sonja removes the operations filter
  Wait until  Click Element  xpath=//div[@data-test-id="operations-filter-component"]//ul[@class="tags"]//li[@class="tag"]//i
  Filter item should contain X number of tags  operations  0

Sonja sets sorting by location
  Click sorting field  Sijainti
  Sorting Should Be Set As  Sijainti  desc
  Click sorting field  Sijainti
  Sorting Should Be Set As  Sijainti  asc

Sonja saves sort-by-location filter
  Save advanced filter  sort-by-location

...saved filters should be open
  Wait Until  Element Should Be Visible  //div[@data-test-id="saved-filter-row-sort-by-location"]
  Wait Until  Element Should Be Visible  //div[@data-test-id="saved-filter-row-MEGA"]

Sonja sets sort-by-location filter as default
  Wait Until  Click by test id  set-sort-by-location-as-default-filter
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(@class,"autocomplete-selection")]//span[contains(text(), "sort-by-location")]

Sonja closes saved filters
  Click by test id  toggle-saved-filters
  Wait Until  Element Should Not Be Visible  //div[@data-test-id="saved-filter-row-MEGA"]

Default filter should be sort-by-location filter
  Reload page
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(@class,"autocomplete-selection")]//span[contains(text(), "sort-by-location")]

...no advanced filters shown
  Wait Until  Element should Not be visible  xpath=//div[@data-test-id="advanced-filters"]

...filters and sorting are set
  Click by test id  toggle-advanced-filters
  Wait Until  Element should be visible  xpath=//div[@data-test-id="advanced-filters"]
  Handler filter should contain text  ${all handlers}
  Filter item should contain X number of tags  areas  0
  Filter item should contain X number of tags  tags  0
  Filter item should contain X number of tags  operations  0
  Filter item should contain X number of tags  organization  0
  Sorting Should Be Set As  Sijainti  asc

Sonja selects MEGA filter
  Select From Autocomplete  div[@data-test-id="select-advanced-filter"]  MEGA
  Wait Until  Handler filter should contain text  ${sonja name}
  Filter item should contain X number of tags  areas  1
  Filter item should contain X number of tags  tags  1
  Filter item should contain X number of tags  operations  1
  Filter item should contain X number of tags  organization  1

Sonja trys to overwrite MEGA filter
  Input text  new-filter-name  MEGA
  Wait Until  Element Should Be Visible  //div[@data-test-id="new-filter-submit-button"]//span[contains(text(), "Nimi on jo käytössä")]


*** Keywords ***
Handler filter should contain text
  [Arguments]  ${text}
  Wait until  Element should be visible  //div[@data-test-id="handler-filter-component"]//span[@class="autocomplete-selection"]//span[contains(text(), "${text}")]

Filter item should contain X number of tags
  [Arguments]  ${filter name}  ${amount}
  Wait until  Xpath should match X times  //div[@data-test-id="${filter name}-filter-component"]//ul[@class="tags"]//li[@class="tag"]  ${amount}

Filter should contain tag
  [Arguments]  ${filter name}  ${text}
  Wait Until  Element Should Contain  xpath=//div[@data-test-id="${filter name}-filter-component"]//ul[@class="tags"]//li[@class="tag"]//span  ${text}

Sorting Should Be Set As
  [Arguments]  ${field name}  ${order}
  Wait until  Element should be visible  //table[@id="applications-list"]//th[contains(text(), "${field name}") and contains(@class, "${order}")]

Click sorting field
  [Arguments]  ${field name}
  Wait Until  Click Element  xpath=//table[@id="applications-list"]//th[contains(text(), "${field name}")]

Save advanced filter
  [Arguments]  ${filter-name}
  Input text  new-filter-name  ${filter-name}
  Wait Until  Click Element  //div[@data-test-id="new-filter-submit-button"]//button
  Wait Until  Element Should Be Visible  //div[@data-test-id="select-advanced-filter"]//span[contains(text(), "${filter-name}")]