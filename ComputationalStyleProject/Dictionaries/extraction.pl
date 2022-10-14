#!/usr/bin/perl

while (<>) {
  @recs = split("\\|", $_);
  $word = substr($recs[0],51);
  $partofspeech = substr($recs[0],44,1);
  if ($partofspeech eq " ") {
    $partofspeech = "?";
  }
  $synpartofspeech = substr($recs[0],45,1);
  if ($synpartofspeech eq " ") {
    $synpartofspeech = "?";
  }
  $capitalization = substr($recs[0],49,1);
  if ($capitalization eq " ") {
    $capitalization = "N";
  }
  print $word . "|" . $partofspeech . "|" . $synpartofspeech . "|" . $capitalization . "\n";
}
