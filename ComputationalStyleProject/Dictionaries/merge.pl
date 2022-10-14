#!/usr/bin/perl

#MANUAL CHANGES:
# delete initial entries like ",COMMA"
# enhance the "%" entry
# add "." as "point"
# examine double entries

open(D1,"<CMU/cmudict.0.6.txt");
open(D2,"<d2.txt");

open(M1,">merged.dct");
open(MISSED,">missed.txt");

$line1 = <D1>;
$line2 = <D2>;
chomp $line1;
chop $line1;
chomp $line2;
while (!eof D1 && !eof D2) {
  @recs1 = split("  ",$line1);
  my @phonemeArray = ();
  @recs2 = split("\\|",$line2);
  $recs2[0] = lc($recs2[0]); #so comparison works
  $recs1[0] = lc($recs1[0]);
  $curWord = $recs1[0];
  push @phonemeArray, ($recs1[1]);
#	print STDOUT @phonemeArray;
  $line1 = <D1>;
  chomp $line1;
  chop $line1;
  @recs1 = split("  ",$line1);
  while (($tmpind = index($recs1[0],"(")) >= 0) {
    $recs1[0] = substr($recs1[0],0,$tmpind);
    $recs1[0] = lc($recs1[0]);
    if ($recs1[0] ne $curWord) {
      last; #"immediately exits the loop in question"
    }
    push @phonemeArray, ($recs1[1]);
    $line1 = <D1>;
    chomp $line1;
    chop $line1;
    @recs1 = split("  ",$line1);
  }

  #recs1: word  phonemes
  #recs2: word|pos|synpos|capzn
  #goal: word|phonemes|capzn|pos|synpos|altphonemes1:altphonemes2:*
 
 while ($recs2[0] && ($recs2[0] lt $curWord)) {
    #entry in MRC that's not in CMU
    print MISSED $line2 . "\n";
    $line2 = <D2>;
    chomp $line2;
    @recs2 = split("\\|",$line2);
    $recs2[0] = lc($recs2[0]); #so comparison works
  }
  if ($curWord eq $recs2[0]) {
    if ($recs2[3] eq "C") {
      $curWord = ucfirst($curWord);
    }
    print M1 $curWord . "|" . $recs2[3] . "|" . $recs2[1] . "|" . $recs2[2] . "|";
    foreach $pronunciation (@phonemeArray) {
      print M1 $pronunciation . ":";
    }
    print M1 "\n";

    $line2 = <D2>;
    chomp $line2;
    @recs2 = split("\\|",$line2);
    $recs2[0] = lc($recs2[0]); #so comparison works
    while ($recs2[0] eq $curWord) {
      #print new line to M1 with prefille @phonemeArray and new recs2 data
      print M1 $curWord . "|" . $recs2[3] . "|" . $recs2[1] . "|" . $recs2[2] . "|";
      foreach $pronunciation (@phonemeArray) {
        print M1 $pronunciation . ":";
      }
      print M1 "\n";
      $line2 = <D2>;
      chomp $line2;
      @recs2 = split("\\|",$line2);
      $recs2[0] = lc($recs2[0]); #so comparison works
    }
  }
  elsif ($curWord lt $recs2[0]) {
    print M1 $curWord . "|?|?|?|";
    foreach $pronunciation (@phonemeArray) {
      print M1 $pronunciation . ":";
    }
    print M1 "\n";
  }
}
if (eof D1) {
  #output rest of D2
  while (<D2>) {
    print MISSED $_;
  }
}
elsif (eof D2) {
  #output rest of D1
  do {
    my @phonemeArray = ();
    @recs1 = split("  ",$line1);
    $curWord = $recs1[0];
    push @phonemeArray, ($recs1[1]);
    $line1 = <D1>;
    chomp $line1;
    chop $line1;
    @recs1 = split("  ",$line1);
    while (($tmpind = index($recs1[0],"(")) >= 0) {
      $recs1[0] = substr($recs1[0],0,$tmpind);
      $recs1[0] = lc($recs1[0]);
      if ($recs1[0] ne $curWord) {
        last; #"immediately exits the loop in question"
      }
      push @phonemeArray, ($recs1[1]);
      $line1 = <D1>;
      chomp $line1;
      chop $line1;
      @recs1 = split("  ",$line1);
    }
    print M1 $curWord . "|?|?|?|";
    foreach $pronunciation (@phonemeArray) {
      print M1 $pronunciation . ":";
    }
    print M1 "\n";
  } while ($line1);
}

close(M1);
close(MISSED);

close(D2);
close(D1);
