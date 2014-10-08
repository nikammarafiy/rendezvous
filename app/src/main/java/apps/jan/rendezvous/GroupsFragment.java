package apps.jan.rendezvous;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * The GroupsFragment class is a wrapper class that
 * supports the functionality required to display the
 * results of a query to the sqlite master on the main
 * UI thread. In this case, we define an adapter that
 * fills listview entries with the names and UUIDs of
 * particular groups that exist on the client.
 */
public class GroupsFragment extends Fragment {

    SQLiteOpenHelper db_helper = main.groupsdb;
    ArrayAdapter<String> adapter;

    ArrayList<String> name_data  = new ArrayList<String>();
    ArrayList<String> group_ids = new ArrayList<String>();

    ListView mGroupList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        populateGroups();
        ListView lv = ( ListView )inflater
                .inflate(R.layout.group_fragment_layout, container, false);
        mGroupList = ( ListView )lv.findViewById(R.id.groups_list);
        GroupArrayAdapter adapter = new GroupArrayAdapter(
                getActivity(), name_data, group_ids);
        mGroupList.setAdapter(adapter);
        return lv;
    }

    /**
     * Nested adapter class to support multiple
     * arrays.
     */
    public class GroupArrayAdapter extends BaseAdapter {
        Context context;
        private ArrayList<String> group_ids;
        private ArrayList<String> group_members;

        public GroupArrayAdapter(Context context, ArrayList<String> members,
                                 ArrayList<String> ids){
            group_ids = ids;
            group_members = members;
            this.context = context;
        }

        @SuppressLint("InflateParams")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null){
                LayoutInflater inflater = (LayoutInflater)
                        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.groups_list_element,null);
            }
            TextView id_text = (TextView) v.findViewById(R.id.curr_group_name);
            TextView member_text = (TextView) v.findViewById(R.id.name_text);

            id_text.setText(group_ids.get(position));
            member_text.setText(group_members.get(position));

            final String curr_group = group_ids.get(position).split("_")[1];
            member_text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!main.currentOpenGroupId.isEmpty() &&
                            !curr_group.equals(main.currentOpenGroupId)){
                        HashMap<String, Marker> temp = main.markerMap.get(main.currentOpenGroupId);
                        if (temp != null) {
                            for (String key : temp.keySet()) {
                                temp.get(key).remove();
                            }
                        }
                    }
                    main.currentOpenGroupId = curr_group;
                    main.sendCurrentLocationToGroup(main.currentOpenGroupId);
                    Log.i("CLIENT", "getView: Sent current location to group");
                }
            });

            return v;
        }

        @Override
        public int getCount() {
            return group_members.size();
        }

        @Override
        public Object getItem(int position) {
            if (position >= group_members.size()){
                return group_ids.get(position);
            }
            return group_members.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

    }

    /**
     * Retrieve the names of members in the
     * sqlite master so that they can be displayed
     * on the UI thread.
     */
    public void populateGroups(){
        SQLiteDatabase db = db_helper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        String[] columns = {GroupsDataFormat.GroupData.COLUMN_ACCOUNT_NAME};
        if (c != null && c.moveToFirst()){
            while (!c.isAfterLast()) {
                String curr_table = c.getString(0);
                if (curr_table.split("_")[0].equals("group")){
                    group_ids.add(curr_table);
                    Cursor curr_c =
                            db.query(curr_table, columns, null, null, null, null, columns[0]);
                    if (curr_c.moveToFirst()){
                        StringBuilder names = new StringBuilder();
                        while (!curr_c.isAfterLast()){
                            if (curr_c.isLast()){
                                names.append(curr_c.getString(GroupsDataFormat.GroupData.ACCOUNT_INDEX));
                            } else {
                                names.append(curr_c.getString(GroupsDataFormat.GroupData.ACCOUNT_INDEX) + ", ");
                            }
                            curr_c.moveToNext();
                        }
                        name_data.add(names.toString());
                    }
                    curr_c.close();
                }
                c.moveToNext();
            }
            c.close();
        }
    }



}
