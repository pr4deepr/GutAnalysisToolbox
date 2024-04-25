//Hyperlinks to get help and support
//need to have an editable field for the message field to show up
#@ String(label="HELP AND SUPPORT", value="Click on the links below for more info or support") name
#@ String (visibility=MESSAGE, value="<html><b>GAT v0.6</b> <br> <b>Step by step tutorial for GAT: </b> <a href='https://gut-analysis-toolbox.gitbook.io/docs/'> Wiki</a><br> <b>Video tutorials</b> <a href='https://www.youtube.com/channel/UC03y9hDwDsVAhgeebyWpoew/playlists'> Youtube Playlist</a><br> <b>Latest updates:</b> <a href='https://github.com/pr4deepr/GutAnalysisToolbox/releases/'> Release notes</a> <br> <a href='https://zenodo.org/doi/10.5281/zenodo.10590347'> Download sample data</a> <br><br><b>SUPPORT</b><br> Any of the following options can be used for getting help or reporting errors:<br><br> * Visit <a href='https://forms.gle/6AampkLzhVJc5ygx9'> this link </a> to use Google forms to report errors<br>* Visit <a href='https://github.com/pr4deepr/GutAnalysisToolbox/issues'> this link </a> to post within the GAT github repository.<br>You will need a Github account for this.<br>* Visit <a href='https://forum.image.sc/'> this link </a> to post in imagesc forum. Include @pr4deepr and @GAT in the post.<br>* Our preprint can be <a href='https://https://www.biorxiv.org/content/10.1101/2024.01.17.576140v1'> accessed here </a>. </html>") google_form

//Also print it out to the table, if the script parameters isn't workingcript parameters and using a GUI window wasn't working
table_name = "Help and Support";
if(!isOpen(table_name)) Table.create(table_name);
else
{
	close(table_name);
	Table.create(table_name);
}

column= "Help";

Table.set(column,0, "Step by step tutorial for GAT:");
Table.set(column,1,"https://gut-analysis-toolbox.gitbook.io/docs/");
Table.set(column,2, "Youtube Tutorials:");
Table.set(column,3,"https://www.youtube.com/channel/UC03y9hDwDsVAhgeebyWpoew/playlists");
Table.set(column,4," ");
Table.set(column,5,"Google FORM:"); 
Table.set(column,6,"https://forms.gle/6AampkLzhVJc5ygx9");
Table.set(column,7,"Github Repository, which you need to create a github account for");
Table.set(column,8,"https://github.com/pr4deepr/GutAnalysisToolbox/issues");
Table.set(column,9,"You can also post in the imagesc forum: https://forum.image.sc/ , just tag or include @pr4deepr and @GAT in the post so we are notified");
Table.set(column,10,"Thank you for using GAT!");
Table.set(column,11,"GAT v0.6");
