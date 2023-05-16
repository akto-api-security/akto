# Test Editor

# Filters

Filters constitute of checks which are performed for checking whether an endpoint is eligible for a particular test. These filter configurations are maintained in yaml files under the setion ("apiSelectionFilters"). These filters can be of the following types - 

1. ## urlContains
    This filter checks whether provided regex is present in url string excluding queryparams. In case of multiple regex's specified, this will try to match atleast one.

2. ## userIdParamRegex
    This is not a filter in itself. A precheck will be triggered in case this condition exists in yaml

3. ## isPrivate
    This is not a filter in itself. A precheck will be triggered in case this condition exists in yaml

4. ## authenticated
    This is not a filter in itself. A precheck will be triggered in case this condition exists in yaml

5. ## respStatus
    This contains a single string which can have values 2xx, 300, 301 etc. Response code will be matched against this value.

6. ## excludeMethods
    This filter contains a list of methods, which are not eligible. If an endpoint matches any of these methods, it's invalid for that particular test

7. ## mustContainKeys
    This filter is a list, and has the following structure.
      mustContainKeys:
        - location: 
          - "request-body"
          - "request-header"
          keyRegex: 
            - "email"
            - "login"
    Location can have the following values - 
    1. request-body - search in request body
    2. response-body - search in reqresponseuest body
    3. request-header - search in request header
    4. response-header - search in response header
    5. queryParam - search in queryParam

    KeyRegex can be any regex string.

    A match is considered to be valid when any of the keyRegex strings is present in atleast 1 location. Also if there are multiple mustContainKey conditions, it's mandatory for each one to match

8. ## versionMatchRegex
    This is a single regex string which should contain a match in the url string to be considered valid.

9. ## paginationKeyWords
    This filter contains list of regex keywords, which are searched inside the queryparams. In case of multiple regex keywords present, any 1 should match to be considered a valid endpoint for the test.

10. ## containsParams

    This filter is a list, and has the following structure.
        containsParams: 
          - param: url
            paramRegex:
              - "https://"
              - "http://"
            searchIn: 
              - request-header
              - request-body
              - queryParam
    
    Param is the type of param which has to be searched (for ex - url)
    
    ParamRegex can be any regex string
    
    SearchIn can contain the following values - 
    1. request-body - search in request body
    2. response-body - search in reqresponseuest body
    3. request-header - search in request header
    4. response-header - search in response header
    5. queryParam - search in queryParam

    
    A match is considered to be valid when any of the paramRegex strings is present in atleast 1 searchIn location. Also if there are multiple containsParams conditions, it's mandatory for each one to match
    