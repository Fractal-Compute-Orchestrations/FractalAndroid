package AppFrontend.Interface.Insights

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import AppFrontend.Interface.Insights.DeviceInsights.DeviceInsightsFragment
import AppFrontend.Interface.Insights.ModelTraining.ModelTrainingFragment
import AppFrontend.Interface.Insights.UsageInsights.UsageInsights_Fragment

class InsightsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    // 0 = Usage Insights (Heartbeat), 1 = Device Insights (Battery), 2 = Model Training (Waves)
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> UsageInsights_Fragment()   // The first screen (Left Tab) // The middle screen (Center Tab)
            1 -> ModelTrainingFragment()    // The third screen (Right Tab)
            else -> ModelTrainingFragment()
        }
    }
}