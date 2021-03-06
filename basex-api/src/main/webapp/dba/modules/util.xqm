(:~
 : Utility functions.
 :
 : @author Christian Grün, BaseX Team, 2014-16
 :)
module namespace util = 'dba/util';

import module namespace Request = 'http://exquery.org/ns/request';
import module namespace cons = 'dba/cons' at 'cons.xqm';

(:~
 : Evaluates a query and returns the result.
 : @param  $query  query
 : @return result of query
 :)
declare function util:query(
  $query  as xs:string?
) as xs:string {
  util:query($query, '', map {})
};

(:~
 : Evaluates a query and returns the result.
 : @param  $query  query
 : @param  $map    bindings
 : @param  $vars   variable bindings
 : @return result of query
 :)
declare function util:query(
  $query  as xs:string?,
  $map    as xs:string,
  $vars   as map(*)
) as xs:string {
  let $limit := $cons:OPTION($cons:K-MAX-CHARS)
  let $query := if($query) then $query else '()'
  let $q := "xquery:eval($query, map {" || $map || "}, " || util:query-options() || ")"
  let $s := "serialize(" || $q || ", map{ 'limit': $limit*2, 'method': 'basex' })"
  return util:eval(
    $s ||
    "! (if(string-length(.) > $limit) then substring(., 1, $limit) || '...' else .)",
    map:merge((map { 'query': $query, 'limit': $limit }, $vars))
  )
};

(:~
 : Runs an updating query.
 : @param  $query  query
 : @return empty sequence
 :)
declare %updating function util:update-query(
  $query  as xs:string?
) {
  let $q := "xquery:update($query, map { }, " || util:query-options() || ")"
  return util:update($q, map { 'query': if($query) then $query else '()' })
};

(:~
 : Returns the options for evaluating a query.
 : @return options
 :)
declare %private function util:query-options() {
  "map { 'timeout':" || $cons:OPTION($cons:K-TIMEOUT) ||
       ",'memory':" || $cons:OPTION($cons:K-MEMORY) ||
       ",'permission':'" || $cons:OPTION($cons:K-PERMISSION) || "' }"
};

(:~
 : Evaluates the specified query locally or on a remote server and returns the results.
 : @param  $query  query to be executed
 : @return result
 :)
declare function util:eval(
  $query  as xs:string
) as item()* {
  util:eval($query, map {})
};

(:~
 : Evaluates the specified query locally or remotely and returns the resulting items.
 : @param  $query  query to be executed
 : @param  $vars   variables
 : @return result
 :)
declare function util:eval(
  $query  as xs:string,
  $vars   as map(*)
) as item()* {
  let $query := string-join((
    map:keys($vars) ! ('declare variable $' || . || ' external;'), $query
  ))
  return if($cons:SESSION/host) then (
    util:remote-query($query, $vars)
  ) else (
    xquery:eval($query, $vars)
  )
};

(:~
 : Evaluates the specified updating query locally or remotely.
 : @param  $query  query to be executed
 : @param  $vars   variables
 :)
declare %updating function util:update(
  $query  as xs:string,
  $vars   as map(*)
) {
  let $query := string-join((
    map:keys($vars) ! ('declare variable $' || . || ' external;'), $query
  ))
  return if($cons:SESSION/host) then (
    prof:void(util:remote-query($query, $vars))
  ) else (
    xquery:update($query, $vars)
  )
};

(:~
 : Evaluates the specified query on a remote server and returns the results.
 : @param  $query  query to be executed
 : @param  $vars   variables
 : @return result
 :)
declare %private function util:remote-query(
  $query  as xs:string,
  $vars   as map(*)
) as item()* {
  let $id := client:connect(
    $cons:SESSION/host, $cons:SESSION/port,
    $cons:SESSION/name, $cons:SESSION/pass
  )
  return (
    client:query($id, $query, $vars),
    client:close($id)
  )
};

(:~
 : Converts the specified binary input to an XML string, or raises an error.
 : @param  $input  input
 : @return XML string
 :)
declare function util:to-xml-string(
  $input  as xs:base64Binary
) as xs:string {
  let $string :=
    try {
      convert:binary-to-string($input)
    } catch * {
      error((), "Input is no valid UTF8 string.")
    }
  return
    try {
      (: tries to convert the input to XML, but discards the results :)
      prof:void(parse-xml($string)),
      $string
    } catch * {
      error((), "Input is no well-formed XML.")
    }
};
