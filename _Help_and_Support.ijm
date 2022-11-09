//Easy access for users to report errors
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