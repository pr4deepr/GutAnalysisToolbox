//Easy access for users to report errors
#@ String (visibility=MESSAGE, value="<html><b>Step by step tutorial for GAT: </b> <a href='https://github.com/pr4deepr/GutAnalysisToolbox/wiki'> Wiki</a><br> <b>Video tutorials</b> <a href='https://www.youtube.com/playlist?list=PLmBt1Dumq60p4mIFT4j7TP_PVRjbO55Oi'> Youtube Playlist</a><br> <b>Latest updates:</b> <a href='https://github.com/pr4deepr/GutAnalysisToolbox/releases/'> Release notes</a> <br><br><b>SUPPORT</b><br> Any of the following options can be used for getting help or reporting errors:<br><br> * Visit <a href='https://forms.gle/6AampkLzhVJc5ygx9'> link 1 </a> to use Google forms to report errors<br>* Visit <a href='https://github.com/pr4deepr/GutAnalysisToolbox/issues'> link 2 </a> to post within the GAT github repository.<br>You will need a Github account for this.<br>* Visit <a href='https://forum.image.sc/'> link 3 </a> to post in imagesc forum. Include @pr4deepr in the post<br><br> </html>") google_form
/* 
//Script parameters and using a GUI window wasn't working
table_name = "Help and Support";
if(!isOpen(table_name)) Table.create(table_name);
else
{
	close(table_name);
	Table.create(table_name);
}

column= "Help";

Table.set(column,0, "Step by step tutorial for GAT:");
Table.set(column,1,"https://github.com/pr4deepr/GutAnalysisToolbox/wiki");
Table.set(column,2, "Youtube Tutorials:");
Table.set(column,3,"https://www.youtube.com/channel/UC03y9hDwDsVAhgeebyWpoew/playlists");
Table.set(column,4," ");
Table.set(column,5,"Google FORM:"); 
Table.set(column,6,"https://forms.gle/6AampkLzhVJc5ygx9");
Table.set(column,7,"Github Repository, which you need to create a github account for");
Table.set(column,8,"https://github.com/pr4deepr/GutAnalysisToolbox/issues");
Table.set(column,9,"You can also post in the imagesc forum: https://forum.image.sc/ , just tag or include @pr4deepr in the post so we are notified");
Table.set(column,10,"Thank you for using GAT!");
*/
