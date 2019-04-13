package com.example.coolweather;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.coolweather.db.City;
import com.example.coolweather.db.County;
import com.example.coolweather.db.Province;
import com.example.coolweather.util.HttpUtil;
import com.example.coolweather.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE=0;
    public static final int LEVEL_CITY=1;
    public static final int LEVEL_COUNTY=2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String>adapter;
    private List<String>dataList=new ArrayList<>();
    private List<Province>provinceList;
    private List<City>cityList;
    private List<County>countyList;
    private Province selectedProvince;
    private City selectedCity;
    private int currentLevel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View view=inflater.inflate (R.layout.choose_area,container,false);
        titleText=(TextView)view.findViewById (R.id.title_text);
         backButton=(Button)view.findViewById (R.id.back_button);
        listView=(ListView)view.findViewById (R.id.list_view);
        adapter=new ArrayAdapter<> (getContext (),android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter (adapter);
        return view;

    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated (savedInstanceState);
        Log.d ("ChooseAreaFragment","第一部分");
        listView.setOnItemClickListener (new AdapterView.OnItemClickListener () {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel ==LEVEL_PROVINCE){
                    selectedProvince=provinceList.get(position);
                    Log.d ("ChooseAreaFragment","开始加载Cities");
                    queryCities();
                }
                else if(currentLevel==LEVEL_CITY){
                    selectedCity=cityList.get (position);
                    queryCounties();
                }else if(currentLevel==LEVEL_COUNTY){
                    String weatherId=countyList.get (position).getWeatherId ();
                    //Log.d ("ChooseAreaFragment",weatherId);
                    if(getActivity ()instanceof MainActivity) {
                        Intent intent = new Intent (getActivity (), WeatherActivity.class);
                        intent.putExtra ("weather_id", weatherId);
                        startActivity (intent);
                        // Log.d ("ChooseAreaFragment","跳转weatherActivity");
                        getActivity ().finish ();
                    }else if(getActivity ()instanceof WeatherActivity){
                        WeatherActivity activity=(WeatherActivity)getActivity ();
                        activity.drawerLayout.closeDrawers ();
                        activity.swipeRefresh.setRefreshing (true);
                        activity.requestWeather(weatherId);
                    }
                }
            }
        });
        backButton.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick(View v) {
                if(currentLevel==LEVEL_COUNTY){
                    queryCities ();
                }else if(currentLevel==LEVEL_CITY){
                    queryProvinces();
                }
            }
        });

        queryProvinces();

    }

    private void queryCounties() {
        titleText.setText (selectedCity.getCityName ());
        backButton.setVisibility (View.VISIBLE);
        countyList=DataSupport.where ("cityid=?",String.valueOf (selectedCity.getId ())).find (County.class);

        if(countyList.size ()>0){
            dataList.clear ();
            for(County county:countyList){
                dataList.add(county.getCountyName ());
            }
            adapter.notifyDataSetChanged ();
            listView.setSelection (0);
            currentLevel=LEVEL_COUNTY;
        }else{
            int provinceCode=selectedProvince.getProvinceCode ();
            int cityCode=selectedCity.getCityCode ();
            String address="http://guolin.tech/api/china/"+provinceCode+"/"+cityCode;
            queryFromSever(address,"county");
        }
    }

    private void queryCities() {
        titleText.setText (selectedProvince.getProvinceName ());
        backButton.setVisibility (View.VISIBLE);
        cityList=DataSupport.where ("provinceid=?",String.valueOf (selectedProvince.getId ())).find (City.class);
        Log.d ("ChooseAreaFragment", "cityList的大小"+String.valueOf (cityList.size ()));
        if(cityList.size ()>0){
            dataList.clear ();
            for(City city:cityList){
                dataList.add(city.getCityName ());
            }
            adapter.notifyDataSetChanged ();
            listView.setSelection (0);
            currentLevel=LEVEL_CITY;
        }else{
            Log.d ("ChooseAreaFragment","使用数据库查询");
            int provinceCode=selectedProvince.getProvinceCode ();
            String address="http://guolin.tech/api/china/"+provinceCode;
            queryFromSever(address,"city");
        }
    }
    private void queryProvinces() {
        Log.d ("ChooseAreaFragment","queryprovinces调用");
        titleText.setText ("中国");
        backButton.setVisibility (View.GONE);


        provinceList=DataSupport.findAll (Province.class);
        Log.d ("ChooseAreaFragment", String.valueOf (provinceList.size ()));
        //Log.d ("ChooseAreaFragment","datesupport");
        if(provinceList.size()>0){
            Log.d ("ChooseAreaFragment","数据库调用");
            dataList.clear ();
            for(Province province:provinceList){
                dataList.add(province.getProvinceName ());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel=LEVEL_PROVINCE;
            Log.d ("ChooseAreaFragment", "LEVEL_PROVINCE"+String.valueOf (currentLevel));
        }else{

            String address="http://guolin.tech/api/china";
            queryFromSever(address,"province");

        }
    }

    private void queryFromSever(String address, final String type) {
        //showProgressDialog();
        Log.d ("ChooseAreaFragment","queryFromServer调用");
        HttpUtil.sendOkHttpRequest (address, new Callback () {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity ().runOnUiThread (new Runnable () {
                    @Override
                    public void run() {
                        closeProgressDialog ();
                        Toast.makeText (getContext (),"加载失败",Toast.LENGTH_SHORT).show ();
                    }
                });

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText=response.body ().string ();
                boolean result=false;
                if("province".equals (type)){
                    result=Utility.handleProvinceResponse (responseText);
                }else if("city".equals (type)){
                    result=Utility.handleCityResponse (responseText,selectedProvince.getId ());
                }else if("county".equals (type)){
                    result=Utility.handleCountyResponse (responseText,selectedCity.getId ());
                }
                if(result){
                    getActivity ().runOnUiThread (new Runnable () {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if("province".equals (type)){
                                queryProvinces ();
                            }else if("city".equals (type)){
                                queryCities ();
                            }else if("county".equals (type)){
                                queryCounties ();
                            }
                        }
                    });
                }

            }
        });
    }

    private void closeProgressDialog() {
        if(progressDialog!=null){
            progressDialog.dismiss ();
        }
    }


    private void showProgressDialog() {
        if (progressDialog==null){
            progressDialog=new ProgressDialog (getActivity ());
            progressDialog.setMessage ("正在加载...");
            progressDialog.setCanceledOnTouchOutside (false);
        }
    }

}

