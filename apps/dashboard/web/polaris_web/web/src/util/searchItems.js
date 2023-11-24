export function generateSearchData(routes) {
    const searchData = [];
  
    function traverseRoutes(routes,parent) {
      routes.forEach(route => {
        let lastChar = parent.charAt(parent.length - 1)
        let path = parent
        if(parent !== "" && lastChar !== "/")
          path = path + "/" + route.path
        else
          path = path + route.path 
        
        if (route.element) {
          searchData.push({
            content: route.element.type.name,
            path: path,
            parent: parent,
          });
        }
  
        if (route.children) {
          traverseRoutes(route.children,path);
        }
      });
    }
  
    traverseRoutes(routes,"");
    return searchData;
}
  